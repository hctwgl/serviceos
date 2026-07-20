package com.serviceos.dispatch.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.configuration.api.DispatchCandidate;
import com.serviceos.configuration.api.DispatchResolution;
import com.serviceos.configuration.api.DispatchResolveCommand;
import com.serviceos.configuration.api.DispatchRuntime;
import com.serviceos.configuration.api.EffectiveDispatchClientKinds;
import com.serviceos.configuration.api.FrozenBundleClientCapabilityProbe;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.dispatch.api.ActivateNetworkFromFrozenDispatchCommand;
import com.serviceos.dispatch.api.ActivateTechnicianFromFrozenDispatchCommand;
import com.serviceos.dispatch.api.NetworkAllocationActualQuery;
import com.serviceos.dispatch.api.NetworkAllocationTargetQuery;
import com.serviceos.dispatch.api.ServiceAssignmentService;
import com.serviceos.network.api.NetworkPortalTechnicianQuery;
import com.serviceos.network.api.NetworkPortalTechnicianView;
import com.serviceos.network.api.ServiceNetworkCoverageQuery;
import com.serviceos.network.api.ServiceNetworkCoverageView;
import com.serviceos.network.api.ServiceNetworkDirectoryQuery;
import com.serviceos.project.api.ProjectNetworkDirectoryQuery;
import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import com.serviceos.workorder.api.WorkOrderExpressionContext;
import com.serviceos.workorder.api.WorkOrderExpressionContextQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M324/M332/M337/M338：task.created → 冻结 DISPATCH → ACTIVE NETWORK，再 → ACTIVE TECHNICIAN。
 *
 * <p>无 dispatchPolicyRef：N/A 完成 Inbox。NETWORK 候选来自项目网点 ∩ ACTIVE 网点 ∩
 * ACTIVE ServiceCoverage（品牌/业务/省市区精确匹配）∩ 容量；无覆盖失败关闭 MANUAL。
 * M338：候选附带月度签约比例缺口（committedShare − actualShare，ORDER_COUNT）。
 * NETWORK 成功后解析网点内师傅；空池/无容量审计 TECHNICIAN MANUAL，保留 NETWORK。
 * TECHNICIAN 阶段仍用 {@code *} 通配候选（师傅覆盖未建模）。
 * M366/ADR-088：自动 TECHNICIAN 池按冻结 Bundle FORM∩EVIDENCE 定向目标硬过滤师傅声明。</p>
 */
@Service
final class DefaultTaskDispatchPolicyEventConsumer implements TaskDispatchPolicyEventConsumer {
    private static final String CONSUMER = "task.dispatch-policy.created.v1";
    private static final String SYSTEM_ACTOR = "system:dispatch-policy";
    private static final String CLIENT_KIND_TARGET_EMPTY = "CLIENT_KIND_TARGET_EMPTY";

    private final TaskFulfillmentContextService tasks;
    private final WorkOrderExpressionContextQuery workOrderContexts;
    private final ProjectNetworkDirectoryQuery projectNetworks;
    private final ServiceNetworkDirectoryQuery serviceNetworks;
    private final ServiceNetworkCoverageQuery coverages;
    private final NetworkAllocationTargetQuery allocationTargets;
    private final NetworkAllocationActualQuery allocationActuals;
    private final NetworkPortalTechnicianQuery technicians;
    private final FrozenBundleClientCapabilityProbe clientCapabilityProbe;
    private final DispatchRuntime dispatchRuntime;
    private final ServiceAssignmentService assignments;
    private final InboxService inbox;
    private final AuditAppender audit;
    private final JdbcClient jdbc;
    private final Clock clock;

    DefaultTaskDispatchPolicyEventConsumer(
            TaskFulfillmentContextService tasks,
            WorkOrderExpressionContextQuery workOrderContexts,
            ProjectNetworkDirectoryQuery projectNetworks,
            ServiceNetworkDirectoryQuery serviceNetworks,
            ServiceNetworkCoverageQuery coverages,
            NetworkAllocationTargetQuery allocationTargets,
            NetworkAllocationActualQuery allocationActuals,
            NetworkPortalTechnicianQuery technicians,
            FrozenBundleClientCapabilityProbe clientCapabilityProbe,
            DispatchRuntime dispatchRuntime,
            ServiceAssignmentService assignments,
            InboxService inbox,
            AuditAppender audit,
            JdbcClient jdbc,
            Clock clock
    ) {
        this.tasks = tasks;
        this.workOrderContexts = workOrderContexts;
        this.projectNetworks = projectNetworks;
        this.serviceNetworks = serviceNetworks;
        this.coverages = coverages;
        this.allocationTargets = allocationTargets;
        this.allocationActuals = allocationActuals;
        this.technicians = technicians;
        this.clientCapabilityProbe = clientCapabilityProbe;
        this.dispatchRuntime = dispatchRuntime;
        this.assignments = assignments;
        this.inbox = inbox;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void applyFrozenPolicy(OutboxMessage message, UUID taskId, Instant createdAt) {
        InboxDecision decision = inbox.begin(
                message.tenantId(), CONSUMER, message.eventId(),
                message.schemaVersion(), message.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) {
            return;
        }

        TaskFulfillmentContext task = tasks.find(message.tenantId(), taskId)
                .orElseThrow(() -> new IllegalStateException("Task does not exist for dispatch policy"));
        if (!"HUMAN".equals(task.taskKind())) {
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|NOT_HUMAN"));
            return;
        }
        if (task.dispatchPolicyRef() == null || task.dispatchPolicyRef().isBlank()) {
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|POLICY_NOT_CONFIGURED"));
            return;
        }
        if (task.configurationBundleId() == null
                || task.configurationBundleDigest() == null
                || task.configurationBundleDigest().isBlank()) {
            throw new IllegalStateException("HUMAN task with dispatchPolicyRef missing frozen Bundle");
        }

        boolean networkActive = hasActiveAssignment(
                message.tenantId(), taskId, "NETWORK");
        boolean technicianActive = hasActiveAssignment(
                message.tenantId(), taskId, "TECHNICIAN");
        if (networkActive && technicianActive) {
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|ALREADY_ASSIGNED"));
            return;
        }

        WorkOrderExpressionContext wo = workOrderContexts.find(message.tenantId(), task.workOrderId())
                .orElseThrow(() -> new IllegalStateException(
                        "WorkOrder expression context missing for dispatch policy"));
        ExpressionContext expressionContext = new ExpressionContext(
                new ExpressionContext.WorkOrderContext(
                        wo.clientCode(), wo.brandCode(), wo.serviceProductCode()),
                new ExpressionContext.RegionContext(
                        wo.provinceCode(), wo.cityCode(), wo.districtCode()),
                new ExpressionContext.TaskContext(task.stageCode(), task.taskType()));
        Instant asOf = clock.instant();

        String networkAssigneeId;
        String networkPolicyEvidence;
        if (!networkActive) {
            NetworkActivation network = activateNetwork(
                    message, task, wo, expressionContext, asOf);
            if (network == null) {
                return;
            }
            networkAssigneeId = network.networkAssigneeId();
            networkPolicyEvidence = network.policyEvidence();
        } else {
            networkAssigneeId = activeAssigneeId(message.tenantId(), taskId, "NETWORK");
            networkPolicyEvidence = "EXISTING_NETWORK|" + networkAssigneeId;
        }

        if (technicianActive) {
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|ALREADY_TECH|" + networkPolicyEvidence));
            return;
        }

        activateTechnician(
                message, task, wo, expressionContext, asOf,
                networkAssigneeId, networkPolicyEvidence);
    }

    private NetworkActivation activateNetwork(
            OutboxMessage message,
            TaskFulfillmentContext task,
            WorkOrderExpressionContext wo,
            ExpressionContext expressionContext,
            Instant asOf
    ) {
        UUID taskId = task.taskId();
        List<String> projectNetworkIds = projectNetworks.listActiveNetworkIds(
                message.tenantId(), task.projectId(), asOf);
        List<String> activeNetworkIds = serviceNetworks.listActiveNetworkIds(
                message.tenantId(), projectNetworkIds);
        List<ServiceNetworkCoverageView> coverageRows = coverages.listActiveCoverage(
                message.tenantId(), activeNetworkIds, wo.brandCode(), wo.serviceProductCode(), asOf);
        Map<String, Set<String>> regionsByNetwork = new LinkedHashMap<>();
        // 省/市/区任一码命中即纳入候选；空值忽略，避免 Set.of(null) NPE 导致 Inbox 回滚。
        Set<String> workOrderRegions = new LinkedHashSet<>();
        if (wo.provinceCode() != null && !wo.provinceCode().isBlank()) {
            workOrderRegions.add(wo.provinceCode().trim());
        }
        if (wo.cityCode() != null && !wo.cityCode().isBlank()) {
            workOrderRegions.add(wo.cityCode().trim());
        }
        if (wo.districtCode() != null && !wo.districtCode().isBlank()) {
            workOrderRegions.add(wo.districtCode().trim());
        }
        for (ServiceNetworkCoverageView row : coverageRows) {
            if (row.regionCode() == null || !workOrderRegions.contains(row.regionCode())) {
                continue;
            }
            String networkId = row.serviceNetworkId().toString();
            regionsByNetwork.computeIfAbsent(networkId, ignored -> new LinkedHashSet<>())
                    .add(row.regionCode());
        }
        Map<String, Double> ratioGaps = allocationRatioGaps(
                message.tenantId(), task.projectId(), wo.brandCode(), wo.serviceProductCode(), asOf);
        List<DispatchCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : regionsByNetwork.entrySet()) {
            CapacitySnapshot capacity = capacitySnapshot(
                    message.tenantId(), "NETWORK", entry.getKey(), wo.serviceProductCode());
            if (capacity == null) {
                continue;
            }
            candidates.add(coverageCandidate(
                    entry.getKey(),
                    wo.brandCode(),
                    wo.serviceProductCode(),
                    entry.getValue(),
                    capacity.remaining(),
                    ratioGaps.getOrDefault(entry.getKey(), 0.0)));
        }

        DispatchResolution resolution = dispatchRuntime.resolve(new DispatchResolveCommand(
                message.tenantId(),
                task.configurationBundleId(),
                task.configurationBundleDigest(),
                task.dispatchPolicyRef(),
                expressionContext,
                candidates));
        String explanation = clipped(String.join("; ", resolution.explanations()));
        String policyEvidence = resolution.assetVersionId() + "|" + resolution.contentDigest();

        if (resolution.requiresManualIntervention() || resolution.rankedCandidates().isEmpty()) {
            auditManual(message, taskId, asOf, "SERVICE_DISPATCH_POLICY_MANUAL",
                    policyEvidence, explanation);
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|MANUAL|" + policyEvidence));
            return null;
        }

        DispatchResolution.RankedCandidate top = resolution.rankedCandidates().getFirst();
        CapacitySnapshot capacity = capacitySnapshot(
                message.tenantId(), "NETWORK", top.candidateId(), wo.serviceProductCode());
        if (capacity == null || capacity.remaining() <= 0) {
            auditManual(message, taskId, asOf, "SERVICE_DISPATCH_POLICY_MANUAL",
                    policyEvidence + "|CAPACITY_RACE", explanation);
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|MANUAL|CAPACITY|" + policyEvidence));
            return null;
        }

        String sourceDecisionId = "decision://dp/" + resolution.assetVersionId()
                + "/" + top.candidateId();
        assignments.activateNetworkFromFrozenDispatchPolicy(
                message.tenantId(),
                message.correlationId(),
                new ActivateNetworkFromFrozenDispatchCommand(
                        task.workOrderId(),
                        taskId,
                        top.candidateId(),
                        wo.serviceProductCode(),
                        sourceDecisionId,
                        capacity.version()));
        audit.append(new AuditEntry(
                UUID.randomUUID(), message.tenantId(), SYSTEM_ACTOR,
                "SERVICE_DISPATCH_POLICY_APPLIED", "dispatch.assignment.manage",
                "Task", taskId.toString(),
                "ALLOW", List.of(), "dispatch-policy-runtime-v1", "APPLIED", null,
                Sha256.digest(policyEvidence + "|" + top.candidateId() + "|" + explanation),
                message.correlationId(), asOf));
        return new NetworkActivation(top.candidateId(), policyEvidence);
    }

    private void activateTechnician(
            OutboxMessage message,
            TaskFulfillmentContext task,
            WorkOrderExpressionContext wo,
            ExpressionContext expressionContext,
            Instant asOf,
            String networkAssigneeId,
            String networkPolicyEvidence
    ) {
        UUID taskId = task.taskId();
        UUID networkId = UUID.fromString(networkAssigneeId);
        // ADR-088 A1-R/A4-R：仅自动 TECHNICIAN 池硬过滤；Manual/Network assign 不走此路径。
        EffectiveDispatchClientKinds kindTarget = clientCapabilityProbe.resolveDispatchTargetClientKinds(
                message.tenantId(),
                task.configurationBundleId(),
                task.configurationBundleDigest(),
                task.formRef());
        List<NetworkPortalTechnicianView> techViews = technicians.listActiveTechnicians(
                message.tenantId(), networkId);
        List<DispatchCandidate> candidates = new ArrayList<>();
        for (NetworkPortalTechnicianView tech : techViews) {
            if (kindTarget.applyFilter()
                    && !DispatchClientKindCompatibility.matchesDeclaredClientKinds(
                            tech.supportedClientKinds(), kindTarget.targetKinds())) {
                continue;
            }
            String assigneeId = tech.technicianProfileId().toString();
            CapacitySnapshot capacity = capacitySnapshot(
                    message.tenantId(), "TECHNICIAN", assigneeId, wo.serviceProductCode());
            if (capacity == null) {
                continue;
            }
            candidates.add(wildcardCandidate(assigneeId, capacity.remaining()));
        }

        if (kindTarget.applyFilter() && candidates.isEmpty()) {
            // A3-R：可解释原因写入 error_code，便于运营与 IT 断言（不仅哈希进 request_digest）。
            String policyEvidence = "client-kind|" + String.join(",", kindTarget.targetKinds());
            audit.append(new AuditEntry(
                    UUID.randomUUID(), message.tenantId(), SYSTEM_ACTOR,
                    "SERVICE_DISPATCH_TECHNICIAN_POLICY_MANUAL", "dispatch.assignment.manage",
                    "Task", taskId.toString(),
                    "ALLOW", List.of(), "dispatch-policy-runtime-v1", "MANUAL",
                    CLIENT_KIND_TARGET_EMPTY,
                    Sha256.digest(policyEvidence + "|" + CLIENT_KIND_TARGET_EMPTY),
                    message.correlationId(), asOf));
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|NET_APPLIED|TECH_MANUAL|" + networkPolicyEvidence
                            + "|" + policyEvidence + "|" + CLIENT_KIND_TARGET_EMPTY));
            return;
        }

        DispatchResolution resolution = dispatchRuntime.resolve(new DispatchResolveCommand(
                message.tenantId(),
                task.configurationBundleId(),
                task.configurationBundleDigest(),
                task.dispatchPolicyRef(),
                expressionContext,
                candidates));
        String explanation = clipped(String.join("; ", resolution.explanations()));
        String policyEvidence = resolution.assetVersionId() + "|" + resolution.contentDigest();

        if (resolution.requiresManualIntervention() || resolution.rankedCandidates().isEmpty()) {
            auditManual(message, taskId, asOf, "SERVICE_DISPATCH_TECHNICIAN_POLICY_MANUAL",
                    policyEvidence, explanation);
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|NET_APPLIED|TECH_MANUAL|" + networkPolicyEvidence
                            + "|" + policyEvidence));
            return;
        }

        DispatchResolution.RankedCandidate top = resolution.rankedCandidates().getFirst();
        CapacitySnapshot capacity = capacitySnapshot(
                message.tenantId(), "TECHNICIAN", top.candidateId(), wo.serviceProductCode());
        if (capacity == null || capacity.remaining() <= 0) {
            auditManual(message, taskId, asOf, "SERVICE_DISPATCH_TECHNICIAN_POLICY_MANUAL",
                    policyEvidence + "|CAPACITY_RACE", explanation);
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|NET_APPLIED|TECH_MANUAL|CAPACITY|"
                            + networkPolicyEvidence + "|" + policyEvidence));
            return;
        }

        String sourceDecisionId = "decision://dpt/" + resolution.assetVersionId()
                + "/" + top.candidateId();
        assignments.activateTechnicianFromFrozenDispatchPolicy(
                message.tenantId(),
                message.correlationId(),
                new ActivateTechnicianFromFrozenDispatchCommand(
                        task.workOrderId(),
                        taskId,
                        top.candidateId(),
                        wo.serviceProductCode(),
                        sourceDecisionId,
                        capacity.version()));
        audit.append(new AuditEntry(
                UUID.randomUUID(), message.tenantId(), SYSTEM_ACTOR,
                "SERVICE_DISPATCH_TECHNICIAN_POLICY_APPLIED", "dispatch.assignment.manage",
                "Task", taskId.toString(),
                "ALLOW", List.of(), "dispatch-policy-runtime-v1", "TECH_APPLIED", null,
                Sha256.digest(policyEvidence + "|" + top.candidateId() + "|" + explanation),
                message.correlationId(), asOf));
        inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                Sha256.digest(taskId + "|APPLIED|" + networkPolicyEvidence + "|"
                        + policyEvidence + "|" + top.candidateId()));
    }

    private void auditManual(
            OutboxMessage message,
            UUID taskId,
            Instant asOf,
            String action,
            String policyEvidence,
            String explanation
    ) {
        audit.append(new AuditEntry(
                UUID.randomUUID(), message.tenantId(), SYSTEM_ACTOR,
                action, "dispatch.assignment.manage",
                "Task", taskId.toString(),
                "ALLOW", List.of(), "dispatch-policy-runtime-v1", "MANUAL", null,
                Sha256.digest(policyEvidence + "|" + explanation),
                message.correlationId(), asOf));
    }

    private boolean hasActiveAssignment(String tenantId, UUID taskId, String level) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM dsp_service_assignment
                     WHERE tenant_id = :tenantId AND task_id = :taskId
                       AND responsibility_level = :level AND status = 'ACTIVE'
                )
                """)
                .param("tenantId", tenantId)
                .param("taskId", taskId)
                .param("level", level)
                .query(Boolean.class)
                .single();
    }

    private String activeAssigneeId(String tenantId, UUID taskId, String level) {
        return jdbc.sql("""
                SELECT assignee_id FROM dsp_service_assignment
                 WHERE tenant_id = :tenantId AND task_id = :taskId
                   AND responsibility_level = :level AND status = 'ACTIVE'
                 ORDER BY created_at
                 LIMIT 1
                """)
                .param("tenantId", tenantId)
                .param("taskId", taskId)
                .param("level", level)
                .query(String.class)
                .single();
    }

    private CapacitySnapshot capacitySnapshot(
            String tenantId, String level, String assigneeId, String businessType
    ) {
        return jdbc.sql("""
                SELECT version, max_units AS "maxUnits", occupied_units AS "occupiedUnits"
                  FROM dsp_capacity_counter
                 WHERE tenant_id = :tenantId
                   AND responsibility_level = :level
                   AND assignee_id = :assigneeId
                   AND business_type = :businessType
                """)
                .param("tenantId", tenantId)
                .param("level", level)
                .param("assigneeId", assigneeId)
                .param("businessType", businessType)
                .query((rs, rowNum) -> new CapacitySnapshot(
                        rs.getLong("version"),
                        rs.getInt("maxUnits") - rs.getInt("occupiedUnits")))
                .optional()
                .orElse(null);
    }

    /**
     * gap = committedShare − actualShare；缺口越大越欠配，正权重下优先。
     * 无目标行的网点 gap=0（中性）；当月总派单为 0 时 actualShare=0。
     */
    private Map<String, Double> allocationRatioGaps(
            String tenantId,
            UUID projectId,
            String brandCode,
            String businessType,
            Instant asOf
    ) {
        Map<String, Double> targets = allocationTargets.listCommittedShares(
                tenantId, projectId, brandCode, businessType, asOf);
        if (targets.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> actuals = allocationActuals.countMonthlyNetworkAssignments(
                tenantId, projectId, brandCode, businessType, asOf);
        long total = actuals.values().stream().mapToLong(Long::longValue).sum();
        Map<String, Double> gaps = new LinkedHashMap<>();
        for (Map.Entry<String, Double> target : targets.entrySet()) {
            double actualShare = total <= 0
                    ? 0.0
                    : actuals.getOrDefault(target.getKey(), 0L) / (double) total;
            gaps.put(target.getKey(), target.getValue() - actualShare);
        }
        return gaps;
    }

    private static DispatchCandidate coverageCandidate(
            String candidateId,
            String brandCode,
            String businessType,
            Set<String> regionCodes,
            int remaining,
            double allocationRatioGap
    ) {
        return new DispatchCandidate(
                candidateId,
                true,
                false,
                true,
                Set.of(brandCode),
                Set.copyOf(regionCodes),
                Set.of(businessType),
                remaining,
                0.0,
                0.0,
                0.0,
                allocationRatioGap);
    }

    private static DispatchCandidate wildcardCandidate(String candidateId, int remaining) {
        return new DispatchCandidate(
                candidateId,
                true,
                false,
                true,
                Set.of("*"),
                Set.of("*"),
                Set.of("*"),
                remaining,
                0.0,
                0.0,
                0.0,
                0.0);
    }

    private static String clipped(String explanation) {
        return explanation.length() > 1800 ? explanation.substring(0, 1800) : explanation;
    }

    private record CapacitySnapshot(long version, int remaining) {
    }

    private record NetworkActivation(String networkAssigneeId, String policyEvidence) {
    }
}
