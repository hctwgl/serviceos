package com.serviceos.dispatch.application;

import com.serviceos.configuration.api.DispatchCandidate;
import com.serviceos.configuration.api.DispatchResolution;
import com.serviceos.configuration.api.DispatchResolveCommand;
import com.serviceos.configuration.api.DispatchRuntime;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.dispatch.api.NetworkAllocationActualQuery;
import com.serviceos.dispatch.api.NetworkAllocationTargetQuery;
import com.serviceos.network.api.ServiceNetworkCoverageQuery;
import com.serviceos.network.api.ServiceNetworkCoverageView;
import com.serviceos.network.api.ServiceNetworkDirectoryQuery;
import com.serviceos.project.api.ProjectNetworkDirectoryQuery;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.workorder.api.WorkOrderExpressionContext;
import com.serviceos.workorder.api.WorkOrderExpressionContextQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NETWORK 派单候选的唯一应用层评估器。
 *
 * <p>自动派单、Admin 候选查询和人工派网点提交都必须经过本评估器。这样可以保证页面展示的候选、
 * 自动选择和命令提交使用同一组不可绕过的项目、网点状态、覆盖、业务类型、容量与冻结策略规则。
 * 这里不做授权：查询和命令入口分别按照自己的 capability/scope 完成授权后调用。</p>
 */
@Component
final class NetworkDispatchCandidateEvaluator {
    private final WorkOrderExpressionContextQuery workOrderContexts;
    private final ProjectNetworkDirectoryQuery projectNetworks;
    private final ServiceNetworkDirectoryQuery serviceNetworks;
    private final ServiceNetworkCoverageQuery coverages;
    private final NetworkAllocationTargetQuery allocationTargets;
    private final NetworkAllocationActualQuery allocationActuals;
    private final DispatchRuntime dispatchRuntime;
    private final JdbcClient jdbc;
    private final Clock clock;

    NetworkDispatchCandidateEvaluator(
            WorkOrderExpressionContextQuery workOrderContexts,
            ProjectNetworkDirectoryQuery projectNetworks,
            ServiceNetworkDirectoryQuery serviceNetworks,
            ServiceNetworkCoverageQuery coverages,
            NetworkAllocationTargetQuery allocationTargets,
            NetworkAllocationActualQuery allocationActuals,
            DispatchRuntime dispatchRuntime,
            JdbcClient jdbc,
            Clock clock
    ) {
        this.workOrderContexts = workOrderContexts;
        this.projectNetworks = projectNetworks;
        this.serviceNetworks = serviceNetworks;
        this.coverages = coverages;
        this.allocationTargets = allocationTargets;
        this.allocationActuals = allocationActuals;
        this.dispatchRuntime = dispatchRuntime;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    Evaluation evaluate(String tenantId, TaskFulfillmentContext task) {
        if (task.dispatchPolicyRef() == null || task.dispatchPolicyRef().isBlank()
                || task.configurationBundleId() == null
                || task.configurationBundleDigest() == null
                || task.configurationBundleDigest().isBlank()) {
            throw new BusinessProblem(
                    ProblemCode.VALIDATION_FAILED,
                    "当前任务缺少已冻结的派单配置，无法计算责任网点候选");
        }

        WorkOrderExpressionContext workOrder = workOrderContexts.find(tenantId, task.workOrderId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND,
                        "工单履约事实不存在，无法计算责任网点候选"));
        ExpressionContext expressionContext = expressionContext(task, workOrder);
        Instant asOf = clock.instant();

        List<String> projectNetworkIds = projectNetworks.listActiveNetworkIds(
                tenantId, task.projectId(), asOf);
        List<String> activeNetworkIds = serviceNetworks.listActiveNetworkIds(
                tenantId, projectNetworkIds);
        List<ServiceNetworkCoverageView> coverageRows = coverages.listActiveCoverage(
                tenantId,
                activeNetworkIds,
                workOrder.brandCode(),
                workOrder.serviceProductCode(),
                asOf);

        Set<String> workOrderRegions = workOrderRegions(workOrder);
        Map<String, Set<String>> regionsByNetwork = new LinkedHashMap<>();
        for (ServiceNetworkCoverageView row : coverageRows) {
            if (row.regionCode() == null || !workOrderRegions.contains(row.regionCode())) {
                continue;
            }
            regionsByNetwork.computeIfAbsent(
                            row.serviceNetworkId().toString(), ignored -> new LinkedHashSet<>())
                    .add(row.regionCode());
        }

        Map<String, Double> ratioGaps = allocationRatioGaps(
                tenantId,
                task.projectId(),
                workOrder.brandCode(),
                workOrder.serviceProductCode(),
                asOf);
        Map<String, CandidateFacts> facts = new LinkedHashMap<>();
        List<DispatchCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : regionsByNetwork.entrySet()) {
            CapacitySnapshot capacity = capacitySnapshot(
                    tenantId, "NETWORK", entry.getKey(), workOrder.serviceProductCode());
            if (capacity == null || capacity.remaining() <= 0) {
                continue;
            }
            facts.put(entry.getKey(), new CandidateFacts(
                    entry.getKey(), Set.copyOf(entry.getValue()), capacity));
            candidates.add(coverageCandidate(
                    entry.getKey(),
                    workOrder.brandCode(),
                    workOrder.serviceProductCode(),
                    entry.getValue(),
                    capacity.remaining(),
                    ratioGaps.getOrDefault(entry.getKey(), 0.0)));
        }

        DispatchResolution resolution = dispatchRuntime.resolve(new DispatchResolveCommand(
                tenantId,
                task.configurationBundleId(),
                task.configurationBundleDigest(),
                task.dispatchPolicyRef(),
                expressionContext,
                candidates));
        return new Evaluation(task, workOrder, asOf, resolution, Map.copyOf(facts));
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
     * gap = committedShare - actualShare。缺口只影响已通过硬过滤的候选排序，不能恢复不合格网点。
     */
    private Map<String, Double> allocationRatioGaps(
            String tenantId,
            java.util.UUID projectId,
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

    private static Set<String> workOrderRegions(WorkOrderExpressionContext workOrder) {
        Set<String> result = new LinkedHashSet<>();
        addRegion(result, workOrder.provinceCode());
        addRegion(result, workOrder.cityCode());
        addRegion(result, workOrder.districtCode());
        return result;
    }

    private static void addRegion(Set<String> result, String regionCode) {
        if (regionCode != null && !regionCode.isBlank()) {
            result.add(regionCode.trim());
        }
    }

    private static ExpressionContext expressionContext(
            TaskFulfillmentContext task, WorkOrderExpressionContext workOrder
    ) {
        return new ExpressionContext(
                new ExpressionContext.WorkOrderContext(
                        workOrder.clientCode(), workOrder.brandCode(), workOrder.serviceProductCode()),
                new ExpressionContext.RegionContext(
                        workOrder.provinceCode(), workOrder.cityCode(), workOrder.districtCode()),
                new ExpressionContext.TaskContext(task.stageCode(), task.taskType()));
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

    record Evaluation(
            TaskFulfillmentContext task,
            WorkOrderExpressionContext workOrder,
            Instant generatedAt,
            DispatchResolution resolution,
            Map<String, CandidateFacts> candidateFacts
    ) {
        CandidateFacts requireAssignable(String networkId) {
            boolean ranked = resolution.rankedCandidates().stream()
                    .anyMatch(candidate -> candidate.candidateId().equals(networkId));
            CandidateFacts facts = candidateFacts.get(networkId);
            if (!ranked || facts == null || facts.capacity().remaining() <= 0) {
                throw new BusinessProblem(
                        ProblemCode.VALIDATION_FAILED,
                        "所选网点当前不符合项目、服务区域、业务类型或容量要求，请刷新候选后重试");
            }
            return facts;
        }
    }

    record CandidateFacts(String networkId, Set<String> regionCodes, CapacitySnapshot capacity) {
    }

    record CapacitySnapshot(long version, int remaining) {
    }
}
