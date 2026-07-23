package com.serviceos.dispatch.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.dispatch.api.NetworkAssignmentCandidateQuery;
import com.serviceos.dispatch.api.NetworkAssignmentCandidateView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkDirectoryLabelQuery;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Admin 责任网点候选读服务；授权、评估和产品显示名在服务端一次完成。 */
@Service
final class DefaultNetworkAssignmentCandidateQuery implements NetworkAssignmentCandidateQuery {
    private static final String READ = "dispatch.read";

    private final TaskFulfillmentContextService tasks;
    private final AuthorizationService authorization;
    private final NetworkDispatchCandidateEvaluator evaluator;
    private final NetworkDirectoryLabelQuery labels;

    DefaultNetworkAssignmentCandidateQuery(
            TaskFulfillmentContextService tasks,
            AuthorizationService authorization,
            NetworkDispatchCandidateEvaluator evaluator,
            NetworkDirectoryLabelQuery labels
    ) {
        this.tasks = tasks;
        this.authorization = authorization;
        this.evaluator = evaluator;
        this.labels = labels;
    }

    @Override
    @Transactional(readOnly = true)
    public NetworkAssignmentCandidateView findCandidates(
            CurrentPrincipal principal, String correlationId, UUID taskId
    ) {
        TaskFulfillmentContext task = tasks.find(principal.tenantId(), taskId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "任务不存在"));
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ,
                principal.tenantId(),
                "Task",
                taskId.toString(),
                task.projectId().toString()), correlationId);

        NetworkDispatchCandidateEvaluator.Evaluation evaluation =
                evaluator.evaluate(principal.tenantId(), task);
        List<UUID> rankedIds = evaluation.resolution().rankedCandidates().stream()
                .map(candidate -> UUID.fromString(candidate.candidateId()))
                .toList();
        Map<UUID, String> names = labels.findNetworkNames(principal.tenantId(), rankedIds);
        boolean directoryIncomplete = evaluation.resolution().rankedCandidates().stream()
                .anyMatch(ranked -> {
                    UUID networkId = UUID.fromString(ranked.candidateId());
                    String name = names.get(networkId);
                    return name == null || name.isBlank()
                            || evaluation.candidateFacts().get(ranked.candidateId()) == null;
                });
        if (directoryIncomplete) {
            // 只隐藏单个缺名候选会改变服务端权威排序并造成静默少选，必须把整次查询标记为数据不完整。
            return new NetworkAssignmentCandidateView(
                    task.taskId(),
                    task.workOrderId(),
                    evaluation.workOrder().serviceProductCode(),
                    evaluation.generatedAt(),
                    "候选网点资料校验未通过",
                    "候选网点资料不完整，请先维护网点名称后重试",
                    List.of());
        }
        List<NetworkAssignmentCandidateView.Candidate> candidates = new ArrayList<>();
        for (var ranked : evaluation.resolution().rankedCandidates()) {
            UUID networkId = UUID.fromString(ranked.candidateId());
            String networkName = names.get(networkId);
            var facts = evaluation.candidateFacts().get(ranked.candidateId());
            candidates.add(new NetworkAssignmentCandidateView.Candidate(
                    networkId,
                    networkName,
                    ranked.rank(),
                    coverageSummary(evaluation, facts),
                    facts.capacity().remaining(),
                    "符合项目、服务区域、业务类型和容量要求，当前推荐顺序第 "
                            + ranked.rank() + " 位"));
        }

        String emptyReason = candidates.isEmpty()
                ? emptyReason(evaluation)
                : null;
        String rankingExplanation = evaluation.resolution().requiresManualIntervention()
                ? "当前策略要求人工确认，请结合候选说明选择责任网点"
                : "候选已通过硬规则校验，并按项目当前派单策略排序";
        return new NetworkAssignmentCandidateView(
                task.taskId(),
                task.workOrderId(),
                evaluation.workOrder().serviceProductCode(),
                evaluation.generatedAt(),
                rankingExplanation,
                emptyReason,
                candidates);
    }

    private static String emptyReason(NetworkDispatchCandidateEvaluator.Evaluation evaluation) {
        if (evaluation.resolution().requiresManualIntervention()) {
            return "当前派单策略未产生可选择网点，需要检查项目网点、服务覆盖、容量或派单配置";
        }
        return "没有符合项目、服务区域、业务类型和容量要求的网点";
    }

    private static String coverageSummary(
            NetworkDispatchCandidateEvaluator.Evaluation evaluation,
            NetworkDispatchCandidateEvaluator.CandidateFacts facts
    ) {
        String district = evaluation.workOrder().districtCode();
        String city = evaluation.workOrder().cityCode();
        if (district != null && facts.regionCodes().contains(district)) {
            return "覆盖工单所在区县";
        }
        if (city != null && facts.regionCodes().contains(city)) {
            return "覆盖工单所在城市";
        }
        return "覆盖工单所在省份";
    }
}
