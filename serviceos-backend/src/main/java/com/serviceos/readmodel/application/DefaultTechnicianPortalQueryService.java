package com.serviceos.readmodel.application;

import com.serviceos.appointment.api.TechnicianContactAttemptQuery;
import com.serviceos.appointment.api.TechnicianScheduleAppointmentQuery;
import com.serviceos.appointment.api.TechnicianScheduleAppointmentView;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.FrozenBundleClientCapabilityProbe;
import com.serviceos.dispatch.api.TechnicianActiveAssignmentQuery;
import com.serviceos.dispatch.api.TechnicianActiveAssignmentView;
import com.serviceos.fieldwork.api.TechnicianVisitHistoryQuery;
import com.serviceos.forms.api.FormSubmissionQueryService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkTechnicianMembershipView;
import com.serviceos.network.api.PrincipalNetworkAffiliationQuery;
import com.serviceos.network.api.TechnicianProfileView;
import com.serviceos.readmodel.api.TechnicianPortalContactAttemptItem;
import com.serviceos.readmodel.api.TechnicianPortalFeedItem;
import com.serviceos.readmodel.api.TechnicianPortalFormSubmissionItem;
import com.serviceos.readmodel.api.TechnicianPortalFeedPage;
import com.serviceos.readmodel.api.TechnicianPortalQueryService;
import com.serviceos.readmodel.api.TechnicianPortalScheduleItem;
import com.serviceos.readmodel.api.TechnicianPortalSchedulePage;
import com.serviceos.readmodel.api.TechnicianPortalSyncSummary;
import com.serviceos.readmodel.api.TechnicianPortalTaskDetail;
import com.serviceos.readmodel.api.TechnicianPortalVisitItem;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import com.serviceos.task.api.TechnicianTaskAssignmentFeedQuery;
import com.serviceos.task.api.TechnicianTaskAssignmentFeedView;
import com.serviceos.workorder.api.WorkOrderExpressionContext;
import com.serviceos.workorder.api.WorkOrderExpressionContextQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Technician Portal Feed 只读编排。
 *
 * <p>事务边界：只读；不写 Outbox/领域事实。鉴权顺序：解析可信
 * {@code X-Technician-Context} → ACTIVE TechnicianProfile + NetworkTechnicianMembership →
 * NETWORK scope {@code task.readAssigned} → 仅当前师傅 assignee 的责任。跨网点失败关闭。</p>
 */
@Service
final class DefaultTechnicianPortalQueryService implements TechnicianPortalQueryService {
    private static final String TASK_READ_ASSIGNED = "task.readAssigned";
    private static final String FORM_READ = "form.read";
    private static final String CONTEXT_PREFIX = "TECHNICIAN|NETWORK|";
    private static final String ITEM_ASSIGNMENT = "ASSIGNMENT";
    private static final String ITEM_TOMBSTONE = "TOMBSTONE";

    private final PrincipalNetworkAffiliationQuery affiliations;
    private final AuthorizationService authorization;
    private final TechnicianActiveAssignmentQuery assignments;
    private final TechnicianTaskAssignmentFeedQuery taskAssignments;
    private final TechnicianScheduleAppointmentQuery appointments;
    private final TechnicianContactAttemptQuery contactAttempts;
    private final TechnicianVisitHistoryQuery visits;
    private final FormSubmissionQueryService formSubmissions;
    private final TaskFulfillmentContextService tasks;
    private final WorkOrderExpressionContextQuery workOrderExpressions;
    private final FrozenBundleClientCapabilityProbe clientCapabilityProbe;
    private final Clock clock;

    DefaultTechnicianPortalQueryService(
            PrincipalNetworkAffiliationQuery affiliations,
            AuthorizationService authorization,
            TechnicianActiveAssignmentQuery assignments,
            TechnicianTaskAssignmentFeedQuery taskAssignments,
            TechnicianScheduleAppointmentQuery appointments,
            TechnicianContactAttemptQuery contactAttempts,
            TechnicianVisitHistoryQuery visits,
            FormSubmissionQueryService formSubmissions,
            TaskFulfillmentContextService tasks,
            WorkOrderExpressionContextQuery workOrderExpressions,
            FrozenBundleClientCapabilityProbe clientCapabilityProbe,
            Clock clock
    ) {
        this.affiliations = affiliations;
        this.authorization = authorization;
        this.assignments = assignments;
        this.taskAssignments = taskAssignments;
        this.appointments = appointments;
        this.contactAttempts = contactAttempts;
        this.visits = visits;
        this.formSubmissions = formSubmissions;
        this.tasks = tasks;
        this.workOrderExpressions = workOrderExpressions;
        this.clientCapabilityProbe = clientCapabilityProbe;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public TechnicianPortalFeedPage taskFeed(
            CurrentPrincipal actor,
            String correlationId,
            String technicianContextHeader,
            String clientKind,
            String sinceCursor
    ) {
        AuthorizedTechnicianContext ctx = requireAuthorizedTechnician(
                actor, correlationId, technicianContextHeader);
        List<TechnicianPortalFeedItem> items;
        if (sinceCursor == null || sinceCursor.isBlank()) {
            items = snapshotFeed(actor.tenantId(), ctx, clientKind);
        } else {
            items = deltaFeed(actor.tenantId(), ctx, clientKind, sinceCursor);
        }
        String nextCursor = items.isEmpty() ? null : items.getLast().cursor();
        return new TechnicianPortalFeedPage(ctx.networkId(), items, nextCursor, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public TechnicianPortalSchedulePage schedule(
            CurrentPrincipal actor, String correlationId, String technicianContextHeader
    ) {
        AuthorizedTechnicianContext ctx = requireAuthorizedTechnician(
                actor, correlationId, technicianContextHeader);
        Set<UUID> taskIds = activeTaskIds(actor.tenantId(), ctx);
        List<TechnicianScheduleAppointmentView> rows =
                appointments.listForTasks(actor.tenantId(), taskIds);
        List<TechnicianPortalScheduleItem> items = rows.stream()
                .map(row -> new TechnicianPortalScheduleItem(
                        row.appointmentId(),
                        row.taskId(),
                        row.workOrderId(),
                        row.projectId(),
                        row.type(),
                        row.status(),
                        row.windowStart(),
                        row.windowEnd(),
                        row.timezone()))
                .toList();
        return new TechnicianPortalSchedulePage(ctx.networkId(), items, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public TechnicianPortalSyncSummary syncSummary(
            CurrentPrincipal actor, String correlationId, String technicianContextHeader
    ) {
        AuthorizedTechnicianContext ctx = requireAuthorizedTechnician(
                actor, correlationId, technicianContextHeader);
        Set<UUID> taskIds = activeTaskIds(actor.tenantId(), ctx);
        int pending = taskIds.size();
        int appointmentWindows = appointments.listForTasks(actor.tenantId(), taskIds).size();
        int tombstones = assignments.countEndedForTechnician(
                actor.tenantId(), ctx.networkId().toString(), ctx.assigneeIds());
        tombstones += networkScopedRevokedTaskAssignments(actor.tenantId(), ctx).size();
        return new TechnicianPortalSyncSummary(
                ctx.networkId(), pending, appointmentWindows, tombstones, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public TechnicianPortalTaskDetail taskDetail(
            CurrentPrincipal actor,
            String correlationId,
            String technicianContextHeader,
            String clientKind,
            UUID taskId
    ) {
        AuthorizedTechnicianContext ctx = requireAuthorizedTechnician(
                actor, correlationId, technicianContextHeader);

        TechnicianActiveAssignmentView serviceAssignment = assignments.listActiveForTechnician(
                        actor.tenantId(), ctx.networkId().toString(), ctx.assigneeIds()).stream()
                .filter(row -> taskId.equals(row.taskId()))
                .findFirst()
                .orElse(null);
        TechnicianTaskAssignmentFeedView taskAssignment = networkScopedActiveTaskAssignments(
                        actor.tenantId(), ctx).stream()
                .filter(row -> taskId.equals(row.taskId()))
                .findFirst()
                .orElse(null);

        // 任务是否存在和是否属于其他师傅/网点不能形成可区分响应，统一按当前上下文不可见处理。
        if (serviceAssignment == null && taskAssignment == null) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "任务不存在");
        }
        TaskFulfillmentContext task = tasks.find(actor.tenantId(), taskId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "任务不存在"));
        // M359：详情头级能力预检，避免进入表单/资料路径后才拒单。
        clientCapabilityProbe.requireCompatible(
                actor.tenantId(),
                clientKind,
                task.configurationBundleId(),
                task.configurationBundleDigest(),
                task.formRef());
        // 表达式白名单工单/区域头与服务端 FormValueValidator 同源；缺失则失败关闭，避免 H5 用空上下文假通过。
        WorkOrderExpressionContext workOrder = workOrderExpressions.find(actor.tenantId(), task.workOrderId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND,
                        "任务关联工单不存在"));
        List<TechnicianPortalScheduleItem> appointmentItems = appointments
                .listForTasks(actor.tenantId(), Set.of(taskId)).stream()
                .map(row -> new TechnicianPortalScheduleItem(
                        row.appointmentId(),
                        row.taskId(),
                        row.workOrderId(),
                        row.projectId(),
                        row.type(),
                        row.status(),
                        row.windowStart(),
                        row.windowEnd(),
                        row.timezone()))
                .toList();
        List<TechnicianPortalContactAttemptItem> contactAttemptItems = contactAttempts
                .listForTasks(actor.tenantId(), Set.of(taskId)).stream()
                .map(row -> new TechnicianPortalContactAttemptItem(
                        row.contactAttemptId(),
                        row.taskId(),
                        row.channel(),
                        row.startedAt(),
                        row.endedAt(),
                        row.resultCode(),
                        row.nextContactAt(),
                        row.createdAt()))
                .toList();
        List<TechnicianPortalVisitItem> visitItems = visits.listForTasks(actor.tenantId(), Set.of(taskId)).stream()
                .map(row -> new TechnicianPortalVisitItem(
                        row.visitId(), row.taskId(), row.appointmentId(), row.visitSequence(), row.status(),
                        row.checkInCapturedAt(), row.checkInReceivedAt(), row.geofenceResult(), row.policyDecision(),
                        row.checkOutCapturedAt(), row.checkOutReceivedAt(), row.resultCode(), row.exceptionCode(),
                        row.aggregateVersion()))
                .toList();
        List<TechnicianPortalFormSubmissionItem> formSubmissionItems = null;
        if (canReadForms(actor, correlationId, task)) {
            formSubmissionItems = formSubmissions.listForTask(actor, correlationId, taskId).stream()
                    .map(row -> new TechnicianPortalFormSubmissionItem(
                            row.submissionId(), row.formVersionId(), row.formKey(), row.submissionVersion(),
                            row.validationStatus(), row.errorCount(), row.warningCount(), row.submittedAt()))
                    .toList();
        }

        return new TechnicianPortalTaskDetail(
                ctx.networkId(),
                task.taskId(),
                task.workOrderId(),
                task.projectId(),
                serviceAssignment == null ? null : serviceAssignment.serviceAssignmentId(),
                taskAssignment == null ? null : taskAssignment.taskAssignmentId(),
                task.taskType(),
                task.taskKind(),
                task.stageCode(),
                task.status(),
                serviceAssignment == null ? null : serviceAssignment.businessType(),
                serviceAssignment != null
                        ? serviceAssignment.effectiveFrom()
                        : taskAssignment.effectiveFrom(),
                task.executionGuarded(),
                task.version(),
                workOrder.clientCode(),
                workOrder.brandCode(),
                workOrder.serviceProductCode(),
                workOrder.provinceCode(),
                workOrder.cityCode(),
                workOrder.districtCode(),
                appointmentItems,
                contactAttemptItems,
                visitItems,
                formSubmissionItems,
                clock.instant());
    }

    private boolean canReadForms(CurrentPrincipal actor, String correlationId, TaskFulfillmentContext task) {
        if (task.projectId() == null) {
            return false;
        }
        return authorization.authorize(actor, AuthorizationRequest.projectCapability(
                        FORM_READ, actor.tenantId(), "Task", task.taskId().toString(), task.projectId().toString()),
                correlationId).effect() == AuthorizationDecision.Effect.ALLOW;
    }

    private List<TechnicianPortalFeedItem> snapshotFeed(
            String tenantId, AuthorizedTechnicianContext ctx, String clientKind
    ) {
        Map<UUID, TechnicianPortalFeedItem> byTask = new LinkedHashMap<>();
        for (TechnicianActiveAssignmentView row : assignments.listActiveForTechnician(
                tenantId, ctx.networkId().toString(), ctx.assigneeIds())) {
            byTask.put(row.taskId(), toAssignmentItem(tenantId, clientKind, row));
        }
        for (TechnicianTaskAssignmentFeedView row : networkScopedActiveTaskAssignments(tenantId, ctx)) {
            byTask.putIfAbsent(row.taskId(), toTaskAssignmentItem(tenantId, clientKind, row));
        }
        return List.copyOf(byTask.values());
    }

    private List<TechnicianPortalFeedItem> deltaFeed(
            String tenantId, AuthorizedTechnicianContext ctx, String clientKind, String sinceCursor
    ) {
        FeedCursor cursor = decodeCursor(sinceCursor);
        List<TechnicianPortalFeedItem> items = new ArrayList<>();
        for (TechnicianActiveAssignmentView row : assignments.listChangesSince(
                tenantId, ctx.networkId().toString(), ctx.assigneeIds(),
                cursor.instant(), cursor.assignmentId())) {
            if ("ENDED".equals(row.status())) {
                items.add(toTombstone(
                        row.taskId(),
                        row.endReasonCode() == null ? "SERVICE_ASSIGNMENT_ENDED" : row.endReasonCode(),
                        encodeCursor(row.effectiveTo(), row.serviceAssignmentId())));
            } else {
                items.add(toAssignmentItem(tenantId, clientKind, row));
            }
        }
        for (TechnicianTaskAssignmentFeedView row : networkScopedRevokedTaskAssignments(tenantId, ctx)) {
            if (row.effectiveTo() == null) {
                continue;
            }
            if (row.effectiveTo().isBefore(cursor.instant())
                    || (row.effectiveTo().equals(cursor.instant())
                    && row.taskAssignmentId().compareTo(cursor.assignmentId()) <= 0)) {
                continue;
            }
            items.add(toTombstone(
                    row.taskId(),
                    row.revokeReasonCode() == null ? "TASK_ASSIGNMENT_REVOKED" : row.revokeReasonCode(),
                    encodeCursor(row.effectiveTo(), row.taskAssignmentId())));
        }
        return List.copyOf(items);
    }

    private Set<UUID> activeTaskIds(String tenantId, AuthorizedTechnicianContext ctx) {
        Set<UUID> taskIds = new LinkedHashSet<>();
        for (TechnicianActiveAssignmentView row : assignments.listActiveForTechnician(
                tenantId, ctx.networkId().toString(), ctx.assigneeIds())) {
            taskIds.add(row.taskId());
        }
        for (TechnicianTaskAssignmentFeedView row : networkScopedActiveTaskAssignments(tenantId, ctx)) {
            taskIds.add(row.taskId());
        }
        return taskIds;
    }

    private List<TechnicianTaskAssignmentFeedView> networkScopedActiveTaskAssignments(
            String tenantId, AuthorizedTechnicianContext ctx
    ) {
        List<TechnicianTaskAssignmentFeedView> candidates =
                taskAssignments.listActiveForPrincipal(tenantId, ctx.principalId().toString());
        Set<UUID> allowed = networkScopedTaskIdSet(tenantId, ctx.networkId(), candidates.stream()
                .map(TechnicianTaskAssignmentFeedView::taskId)
                .toList());
        return candidates.stream().filter(row -> allowed.contains(row.taskId())).toList();
    }

    private List<TechnicianTaskAssignmentFeedView> networkScopedRevokedTaskAssignments(
            String tenantId, AuthorizedTechnicianContext ctx
    ) {
        List<TechnicianTaskAssignmentFeedView> candidates =
                taskAssignments.listRevokedForPrincipal(tenantId, ctx.principalId().toString());
        Set<UUID> allowed = networkScopedTaskIdSet(tenantId, ctx.networkId(), candidates.stream()
                .map(TechnicianTaskAssignmentFeedView::taskId)
                .toList());
        return candidates.stream().filter(row -> allowed.contains(row.taskId())).toList();
    }

    private Set<UUID> networkScopedTaskIdSet(String tenantId, UUID networkId, List<UUID> candidates) {
        return new HashSet<>(assignments.filterTaskIdsForNetwork(
                tenantId, networkId.toString(), candidates));
    }

    private TechnicianPortalFeedItem toAssignmentItem(
            String tenantId, String clientKind, TechnicianActiveAssignmentView row
    ) {
        TaskFulfillmentContext task = tasks.find(tenantId, row.taskId()).orElse(null);
        String cursor = encodeCursor(row.effectiveFrom(), row.serviceAssignmentId());
        return new TechnicianPortalFeedItem(
                ITEM_ASSIGNMENT,
                row.taskId(),
                row.workOrderId(),
                task == null ? null : task.projectId(),
                row.serviceAssignmentId(),
                null,
                task == null ? null : task.taskType(),
                task == null ? null : task.taskKind(),
                task == null ? null : task.stageCode(),
                task == null ? null : task.status(),
                row.businessType(),
                row.effectiveFrom(),
                cursor,
                null,
                capabilityDetail(tenantId, clientKind, task));
    }

    private TechnicianPortalFeedItem toTaskAssignmentItem(
            String tenantId, String clientKind, TechnicianTaskAssignmentFeedView row
    ) {
        TaskFulfillmentContext task = tasks.find(tenantId, row.taskId()).orElse(null);
        String cursor = encodeCursor(row.effectiveFrom(), row.taskAssignmentId());
        return new TechnicianPortalFeedItem(
                ITEM_ASSIGNMENT,
                row.taskId(),
                row.workOrderId(),
                task == null ? null : task.projectId(),
                null,
                row.taskAssignmentId(),
                task == null ? null : task.taskType(),
                task == null ? null : task.taskKind(),
                task == null ? null : task.stageCode(),
                task == null ? null : task.status(),
                null,
                row.effectiveFrom(),
                cursor,
                null,
                capabilityDetail(tenantId, clientKind, task));
    }

    private String capabilityDetail(String tenantId, String clientKind, TaskFulfillmentContext task) {
        if (task == null) {
            return null;
        }
        return clientCapabilityProbe.findIncompatibilityDetail(
                        tenantId,
                        clientKind,
                        task.configurationBundleId(),
                        task.configurationBundleDigest(),
                        task.formRef())
                .orElse(null);
    }

    private static TechnicianPortalFeedItem toTombstone(UUID taskId, String reason, String cursor) {
        return new TechnicianPortalFeedItem(
                ITEM_TOMBSTONE,
                taskId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                cursor,
                reason,
                null);
    }

    private AuthorizedTechnicianContext requireAuthorizedTechnician(
            CurrentPrincipal actor, String correlationId, String technicianContextHeader
    ) {
        UUID networkId = parseTechnicianContext(technicianContextHeader);
        UUID principalId = requirePrincipalUuid(actor);
        Instant at = clock.instant();
        TechnicianProfileView profile = affiliations.findActiveTechnicianProfile(
                actor.tenantId(), principalId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID,
                        "当前主体没有有效的 TechnicianProfile"));
        boolean member = affiliations.listActiveTechnicianMemberships(
                        actor.tenantId(), profile.id(), at).stream()
                .map(NetworkTechnicianMembershipView::serviceNetworkId)
                .anyMatch(networkId::equals);
        if (!member) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID,
                    "当前主体不能使用请求的 Technician Portal 上下文");
        }
        authorization.require(actor, AuthorizationRequest.networkCapability(
                        TASK_READ_ASSIGNED, actor.tenantId(), "ServiceNetwork",
                        networkId.toString(), networkId.toString()),
                correlationId);
        List<String> assigneeIds = List.of(principalId.toString(), profile.id().toString());
        return new AuthorizedTechnicianContext(networkId, principalId, profile.id(), assigneeIds);
    }

    private static UUID parseTechnicianContext(String header) {
        if (header == null || header.isBlank()) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID, "缺少 X-Technician-Context");
        }
        String raw = header.trim();
        String uuidPart = raw;
        if (raw.startsWith(CONTEXT_PREFIX)) {
            uuidPart = raw.substring(CONTEXT_PREFIX.length());
        } else if (raw.contains("|")) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID, "Technician Portal 上下文形态无效");
        }
        try {
            return UUID.fromString(uuidPart);
        } catch (IllegalArgumentException ex) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID, "Technician Portal 上下文形态无效");
        }
    }

    private static UUID requirePrincipalUuid(CurrentPrincipal actor) {
        try {
            return UUID.fromString(actor.principalId());
        } catch (IllegalArgumentException ex) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID,
                    "当前主体无法形成 Technician Portal 上下文");
        }
    }

    private static String encodeCursor(Instant instant, UUID id) {
        Instant ts = instant == null ? Instant.EPOCH : instant;
        String raw = ts.toEpochMilli() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static FeedCursor decodeCursor(String opaque) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(opaque), StandardCharsets.UTF_8);
            int sep = raw.indexOf(':');
            if (sep <= 0) {
                throw new IllegalArgumentException("bad cursor");
            }
            Instant instant = Instant.ofEpochMilli(Long.parseLong(raw.substring(0, sep)));
            UUID id = UUID.fromString(raw.substring(sep + 1));
            return new FeedCursor(instant, id);
        } catch (RuntimeException ex) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "sinceCursor 无效");
        }
    }

    private record AuthorizedTechnicianContext(
            UUID networkId,
            UUID principalId,
            UUID technicianProfileId,
            List<String> assigneeIds
    ) {
    }

    private record FeedCursor(Instant instant, UUID assignmentId) {
    }
}
