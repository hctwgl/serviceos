package com.serviceos.readmodel.application;

import com.serviceos.reliability.spi.PublishedOutboxEventReader;
import com.serviceos.reliability.spi.PublishedOutboxEventReader.PublishedOutboxEvent;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 工单时间线 dead letter 幂等重放与 FAILED 恢复。
 *
 * <p>源事件仍为 PUBLISHED 时按 eventId 重投影并标记 REPLAYED；源缺失则 DISCARDED，
 * 不伪造投影成功。全部开放 DL 消除后，可将 FAILED 恢复为 RUNNING，并清理
 * {@code rebuild_generation > active} 的孤儿投影。</p>
 */
@Service
public class WorkOrderTimelineDeadLetterReplayService {
    private final WorkOrderTimelineProjectionRuntime projectionRuntime;
    private final PublishedOutboxEventReader publishedEvents;
    private final WorkOrderTimelineRebuildProjector timelineProjector;
    private final WorkOrderTimelineRepository timelines;
    private final Clock clock;

    WorkOrderTimelineDeadLetterReplayService(
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

    public ReplayBatchResult replayOpen() {
        // 确认 definition 已登记，避免在未迁移环境误跑重放。
        projectionRuntime.requireDefinition();
        List<WorkOrderTimelineProjectionRuntime.DeadLetter> open =
                projectionRuntime.listOpenDeadLetters();
        int replayed = 0;
        int discarded = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        Instant now = clock.instant();
        for (WorkOrderTimelineProjectionRuntime.DeadLetter deadLetter : open) {
            Optional<PublishedOutboxEvent> source =
                    publishedEvents.findPublishedByEventId(deadLetter.eventId());
            if (source.isEmpty()) {
                projectionRuntime.resolveDeadLetter(
                        deadLetter.deadLetterId(), "DISCARDED", now);
                discarded++;
                continue;
            }
            try {
                timelineProjector.projectForRebuild(
                        source.get().message(), deadLetter.rebuildGeneration());
                projectionRuntime.resolveDeadLetter(
                        deadLetter.deadLetterId(), "REPLAYED", now);
                replayed++;
            } catch (RuntimeException exception) {
                // 保持 PENDING，由 recordDeadLetter 冲突路径增加 attempt；此处只计数。
                projectionRuntime.recordDeadLetter(
                        deadLetter.tenantId(),
                        deadLetter.rebuildGeneration(),
                        deadLetter.eventId(),
                        deadLetter.payloadDigest(),
                        deadLetter.eventType(),
                        deadLetter.schemaVersion(),
                        exception.getClass().getSimpleName(),
                        now);
                failed++;
                errors.add(deadLetter.eventId() + ":" + exception.getMessage());
            }
        }
        return new ReplayBatchResult(replayed, discarded, failed, List.copyOf(errors));
    }

    /**
     * 无开放 dead letter 时从 FAILED 恢复 RUNNING，并清理高于 active 的孤儿 generation。
     */
    public RecoverResult recoverFromFailed() {
        projectionRuntime.requireDefinition();
        if (projectionRuntime.hasAnyOpenDeadLetters()) {
            throw new IllegalStateException("仍有未解决 dead letter，不能从 FAILED 恢复");
        }
        WorkOrderTimelineProjectionRuntime.ProjectionState state = projectionRuntime.requireState();
        if (!"FAILED".equals(state.status())) {
            throw new IllegalStateException("投影不在 FAILED，不能恢复");
        }
        Instant now = clock.instant();
        projectionRuntime.markRecoveredFromFailed(now);
        long cleanedEntries = 0;
        long cleanedCheckpoints = 0;
        // 失败重建可能留下 generation=active+1 的半成品，必须在恢复后清掉。
        int orphan = state.activeGeneration() + 1;
        cleanedEntries += timelines.deleteGeneration(orphan);
        cleanedCheckpoints += projectionRuntime.deleteCheckpointsForGeneration(orphan);
        return new RecoverResult(state.activeGeneration(), cleanedEntries, cleanedCheckpoints);
    }

    public record ReplayBatchResult(int replayed, int discarded, int failed, List<String> errors) {
        public ReplayBatchResult {
            errors = List.copyOf(errors);
        }
    }

    public record RecoverResult(int activeGeneration, long cleanedEntries, long cleanedCheckpoints) {
    }
}
