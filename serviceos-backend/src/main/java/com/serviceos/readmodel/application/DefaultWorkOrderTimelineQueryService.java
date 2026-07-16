package com.serviceos.readmodel.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.WorkOrderTimelineItem;
import com.serviceos.readmodel.api.WorkOrderTimelinePage;
import com.serviceos.readmodel.api.WorkOrderTimelineQueryService;
import com.serviceos.workorder.api.WorkOrderDetail;
import com.serviceos.workorder.api.WorkOrderQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
final class DefaultWorkOrderTimelineQueryService implements WorkOrderTimelineQueryService {
    private final WorkOrderQueryService workOrders;
    private final WorkOrderTimelineRepository timelines;
    private final WorkOrderTimelineProjectionRuntime projectionRuntime;
    private final Clock clock;

    DefaultWorkOrderTimelineQueryService(
            WorkOrderQueryService workOrders,
            WorkOrderTimelineRepository timelines,
            WorkOrderTimelineProjectionRuntime projectionRuntime,
            Clock clock
    ) {
        this.workOrders = workOrders;
        this.timelines = timelines;
        this.projectionRuntime = projectionRuntime;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public WorkOrderTimelinePage list(
            CurrentPrincipal principal,
            String correlationId,
            UUID workOrderId,
            String cursor,
            int limit
    ) {
        Objects.requireNonNull(workOrderId, "workOrderId must not be null");
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }

        // cursor 只定位读取位置；每页均重新执行 M68 的 tenant 隔离与实时 Project Scope 鉴权。
        WorkOrderDetail workOrder = workOrders.get(principal, correlationId, workOrderId);
        WorkOrderTimelineProjectionRuntime.ProjectionState state = projectionRuntime.requireState();
        int generation = state.activeGeneration();
        Cursor decoded = decodeCursor(cursor, workOrderId);
        List<WorkOrderTimelineItem> fetched = timelines.findPage(
                principal.tenantId(),
                workOrderId,
                generation,
                decoded == null ? null : decoded.occurredAt(),
                decoded == null ? null : decoded.entryId(),
                limit + 1);
        boolean hasMore = fetched.size() > limit;
        List<WorkOrderTimelineItem> selected = hasMore ? fetched.subList(0, limit) : fetched;
        WorkOrderTimelineItem last = hasMore ? selected.getLast() : null;
        return new WorkOrderTimelinePage(
                workOrder.workOrder().version(),
                selected,
                last == null ? null : encodeCursor(workOrderId, last.occurredAt(), last.id()),
                clock.instant(),
                timelines.findLastProjectedAt(principal.tenantId(), workOrderId, generation),
                freshness(principal.tenantId(), state, generation));
    }

    private String freshness(
            String tenantId,
            WorkOrderTimelineProjectionRuntime.ProjectionState state,
            int generation
    ) {
        if ("REBUILDING".equals(state.status())) {
            return "REBUILDING";
        }
        if ("FAILED".equals(state.status())
                || projectionRuntime.hasOpenDeadLetters(tenantId, generation)) {
            return "LAGGING";
        }
        if (projectionRuntime.findCheckpoint(tenantId, generation).isEmpty()) {
            return "UNKNOWN";
        }
        return "FRESH";
    }

    private static String encodeCursor(UUID workOrderId, Instant occurredAt, UUID entryId) {
        String value = workOrderId + "|" + occurredAt + "|" + entryId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decodeCursor(String cursor, UUID workOrderId) {
        if (cursor == null) {
            return null;
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 3 || !workOrderId.toString().equals(parts[0])) {
                throw new IllegalArgumentException();
            }
            return new Cursor(Instant.parse(parts[1]), UUID.fromString(parts[2]));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "cursor is invalid for the requested work order timeline", exception);
        }
    }

    private record Cursor(Instant occurredAt, UUID entryId) {
    }
}
