package com.serviceos.sla.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.sla.api.SlaClockSegmentItem;
import com.serviceos.sla.api.SlaInstanceDetail;
import com.serviceos.sla.api.SlaInstanceItem;
import com.serviceos.sla.api.SlaInstancePage;
import com.serviceos.sla.api.SlaInstanceQuery;
import com.serviceos.sla.api.SlaMilestoneItem;
import com.serviceos.sla.api.SlaQueryService;
import com.serviceos.workorder.api.WorkOrderScope;
import com.serviceos.workorder.api.WorkOrderScopeQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * M62 SLA 授权只读投影。
 *
 * <p>列表必须先以显式 project 或工单权威 project 做一次实时授权，再执行单条范围化 SQL，避免逐行鉴权；
 * 详情先按 tenant 隔离读取，再按行内冻结 project 实时鉴权。动态剩余/逾期秒数只使用服务端 Clock，
 * 不接受客户端时间，也不会把已过 deadline 的 RUNNING 行擅自改写为 BREACHED。</p>
 */
@Service
final class DefaultSlaQueryService implements SlaQueryService {
    private static final String READ = "sla.read";
    private static final Set<String> STATUSES = Set.of("RUNNING", "BREACHED", "MET", "MET_LATE");

    private final SlaQueryRepository repository;
    private final WorkOrderScopeQuery workOrders;
    private final AuthorizationService authorization;
    private final Clock clock;

    DefaultSlaQueryService(
            SlaQueryRepository repository,
            WorkOrderScopeQuery workOrders,
            AuthorizationService authorization,
            Clock clock
    ) {
        this.repository = repository;
        this.workOrders = workOrders;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public SlaInstancePage list(
            CurrentPrincipal principal, String correlationId, SlaInstanceQuery query
    ) {
        Objects.requireNonNull(query, "query must not be null");
        String status = normalizeStatus(query.status());
        requireRead(principal, correlationId, query.projectId(), "collection");
        return page(principal.tenantId(), query.projectId(), null, status, query.cursor(), query.limit());
    }

    @Override
    @Transactional(readOnly = true)
    public SlaInstancePage listForWorkOrder(
            CurrentPrincipal principal,
            String correlationId,
            UUID workOrderId,
            String cursor,
            int limit
    ) {
        Objects.requireNonNull(workOrderId, "workOrderId must not be null");
        validateLimit(limit);
        WorkOrderScope scope = workOrders.find(principal.tenantId(), workOrderId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "WorkOrder does not exist"));
        requireRead(principal, correlationId, scope.projectId(), workOrderId.toString());
        return page(principal.tenantId(), scope.projectId(), workOrderId, null, cursor, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public SlaInstanceDetail get(
            CurrentPrincipal principal, String correlationId, UUID slaInstanceId
    ) {
        Objects.requireNonNull(slaInstanceId, "slaInstanceId must not be null");
        SlaStoredInstance stored = repository.findById(principal.tenantId(), slaInstanceId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "SlaInstance does not exist"));
        requireRead(principal, correlationId, stored.projectId(), slaInstanceId.toString());
        Instant asOf = clock.instant();
        List<SlaClockSegmentItem> segments = repository.findSegments(
                        principal.tenantId(), slaInstanceId).stream()
                .map(row -> new SlaClockSegmentItem(
                        row.segmentId(), row.segmentNo(), row.segmentType(), row.startedAt(),
                        row.endedAt(), row.elapsedSeconds(), row.startEventId(), row.endEventId()))
                .toList();
        List<SlaMilestoneItem> milestones = repository.findMilestones(
                        principal.tenantId(), slaInstanceId).stream()
                .map(row -> new SlaMilestoneItem(
                        row.milestoneId(), row.milestoneType(), row.scheduledAt(), row.status(),
                        row.triggeredAt(), row.detectedAt(), row.triggerEventId()))
                .toList();
        return new SlaInstanceDetail(item(stored, asOf), segments, milestones, asOf);
    }

    private SlaInstancePage page(
            String tenantId,
            UUID projectId,
            UUID workOrderId,
            String status,
            String cursorValue,
            int limit
    ) {
        Cursor cursor = decode(cursorValue, projectId, workOrderId, status);
        List<SlaStoredInstance> fetched = repository.findPage(
                tenantId, projectId, workOrderId, status,
                cursor == null ? null : cursor.deadlineAt(),
                cursor == null ? null : cursor.slaInstanceId(), limit + 1);
        boolean more = fetched.size() > limit;
        List<SlaStoredInstance> selected = more ? fetched.subList(0, limit) : fetched;
        Instant asOf = clock.instant();
        List<SlaInstanceItem> items = selected.stream().map(row -> item(row, asOf)).toList();
        SlaStoredInstance last = more ? selected.getLast() : null;
        return new SlaInstancePage(items, last == null ? null : encode(
                projectId, workOrderId, status, last.deadlineAt(), last.slaInstanceId()), asOf);
    }

    private SlaInstanceItem item(SlaStoredInstance row, Instant asOf) {
        Long remainingSeconds = null;
        Long overdueSeconds = null;
        if ("RUNNING".equals(row.status())) {
            remainingSeconds = Math.max(0, Duration.between(asOf, row.deadlineAt()).getSeconds());
            if (asOf.isAfter(row.deadlineAt())) {
                overdueSeconds = Duration.between(row.deadlineAt(), asOf).getSeconds();
            }
        } else if ("BREACHED".equals(row.status())) {
            overdueSeconds = Math.max(0, Duration.between(row.deadlineAt(), asOf).getSeconds());
        } else if ("MET_LATE".equals(row.status())) {
            overdueSeconds = Duration.between(row.deadlineAt(), row.completedAt()).getSeconds();
        }
        return new SlaInstanceItem(
                row.slaInstanceId(), row.projectId(), row.workOrderId(), row.taskId(), row.slaRef(),
                row.policyVersionId(), row.policySemanticVersion(), row.policyContentDigest(),
                row.clockMode(), row.targetDurationSeconds(), row.startedAt(), row.deadlineAt(),
                row.status(), row.breachedAt(), row.breachDetectedAt(), row.completedAt(),
                row.elapsedSeconds(), remainingSeconds, overdueSeconds, row.aggregateVersion(), List.of());
    }

    private void requireRead(
            CurrentPrincipal principal, String correlationId, UUID projectId, String resourceId
    ) {
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), "SlaInstance", resourceId, projectId.toString()), correlationId);
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase();
        if (!STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("status is invalid");
        }
        return normalized;
    }

    private static void validateLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
    }

    private static String encode(
            UUID projectId, UUID workOrderId, String status, Instant deadlineAt, UUID instanceId
    ) {
        String raw = projectId + "|" + nullable(workOrderId) + "|" + nullable(status)
                + "|" + deadlineAt + "|" + instanceId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decode(
            String value, UUID projectId, UUID workOrderId, String status
    ) {
        if (value == null || value.isBlank()) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 5
                    || !projectId.equals(UUID.fromString(parts[0]))
                    || !nullable(workOrderId).equals(parts[1])
                    || !nullable(status).equals(parts[2])) {
                throw new IllegalArgumentException();
            }
            return new Cursor(Instant.parse(parts[3]), UUID.fromString(parts[4]));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("cursor is invalid for the requested SLA scope", exception);
        }
    }

    private static String nullable(Object value) {
        return value == null ? "-" : value.toString();
    }

    private record Cursor(Instant deadlineAt, UUID slaInstanceId) {
    }
}
