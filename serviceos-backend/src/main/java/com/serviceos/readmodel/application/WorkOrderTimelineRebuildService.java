package com.serviceos.readmodel.application;

import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.PublishedOutboxEventReader;
import com.serviceos.reliability.spi.PublishedOutboxEventReader.PublishedOutboxEvent;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 工单时间线 generation 重建作业。
 *
 * <p>不在外层持有长事务：状态切换与逐条投影各自短事务提交。任一条失败则登记 dead letter、
 * 标记 FAILED，且不切换 active generation，旧投影继续可读。切换成功后立即清理旧 generation
 * 条目与 checkpoint（M86；不引入长观察窗）。</p>
 */
@Service
public class WorkOrderTimelineRebuildService {
    private static final int PAGE_SIZE = 100;

    private final WorkOrderTimelineProjectionRuntime projectionRuntime;
    private final PublishedOutboxEventReader publishedEvents;
    private final WorkOrderTimelineRebuildProjector timelineProjector;
    private final WorkOrderTimelineRepository timelines;
    private final Clock clock;

    WorkOrderTimelineRebuildService(
            WorkOrderTimelineProjectionRuntime projectionRuntime,
            PublishedOutboxEventReader publishedEvents,
            WorkOrderTimelineRebuildProjector timelineProjector,
            WorkOrderTimelineRepository timelines,
            Clock clock
    ) {
        this.projectionRuntime = projectionRuntime;
        this.publishedEvents = publishedEvents;
        this.timelineProjector = timelineProjector;
        this.timelines = timelines;
        this.clock = clock;
    }

    public RebuildResult rebuild() {
        projectionRuntime.requireDefinition();
        WorkOrderTimelineProjectionRuntime.ProjectionState state = projectionRuntime.requireState();
        int previousGeneration = state.activeGeneration();
        int targetGeneration = previousGeneration + 1;
        Instant startedAt = clock.instant();
        projectionRuntime.markRebuilding(targetGeneration, startedAt);

        UUID afterOutboxId = null;
        Instant afterCreatedAt = null;
        long projected = 0;
        try {
            while (true) {
                List<PublishedOutboxEvent> page = publishedEvents.scanPublished(
                        WorkOrderCoreTimelineHandler.supportedEventTypes(),
                        afterOutboxId,
                        afterCreatedAt,
                        PAGE_SIZE);
                if (page.isEmpty()) {
                    break;
                }
                for (PublishedOutboxEvent event : page) {
                    OutboxMessage message = event.message();
                    try {
                        timelineProjector.projectForRebuild(message, targetGeneration);
                        projected++;
                    } catch (RuntimeException exception) {
                        projectionRuntime.recordDeadLetter(
                                message.tenantId(),
                                targetGeneration,
                                message.eventId(),
                                message.payloadDigest(),
                                message.eventType(),
                                message.schemaVersion(),
                                exception.getClass().getSimpleName(),
                                clock.instant());
                        projectionRuntime.markFailed(
                                exception.getClass().getSimpleName(), clock.instant());
                        return new RebuildResult(
                                false, previousGeneration, targetGeneration, projected,
                                exception.getMessage(), 0, 0);
                    }
                }
                PublishedOutboxEvent last = page.getLast();
                afterOutboxId = last.message().outboxId();
                afterCreatedAt = last.createdAt();
                if (page.size() < PAGE_SIZE) {
                    break;
                }
            }

            Instant completedAt = clock.instant();
            projectionRuntime.markRunning(targetGeneration, completedAt);
            long cleanedEntries = timelines.deleteGeneration(previousGeneration);
            long cleanedCheckpoints =
                    projectionRuntime.deleteCheckpointsForGeneration(previousGeneration);
            return new RebuildResult(
                    true, targetGeneration, targetGeneration, projected, null,
                    cleanedEntries, cleanedCheckpoints);
        } catch (RuntimeException exception) {
            projectionRuntime.markFailed(exception.getClass().getSimpleName(), clock.instant());
            throw exception;
        }
    }

    public record RebuildResult(
            boolean switched,
            int activeGeneration,
            int attemptedGeneration,
            long projectedEvents,
            String errorMessage,
            long cleanedEntries,
            long cleanedCheckpoints
    ) {
    }
}
