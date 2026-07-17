package com.serviceos.evidence.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.evidence.api.EvidenceItemQueryService;
import com.serviceos.evidence.api.EvidenceItemSummaryView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** EvidenceItem 元数据查询；不加载 Revision、文件引用、采集元数据或校验明细。 */
@Service
final class DefaultEvidenceItemQueryService implements EvidenceItemQueryService {
    private static final String READ = "evidence.read";

    private final EvidenceItemRepository items;
    private final EvidenceSlotRepository slots;
    private final TaskFulfillmentContextService tasks;
    private final AuthorizationService authorization;

    DefaultEvidenceItemQueryService(
            EvidenceItemRepository items,
            EvidenceSlotRepository slots,
            TaskFulfillmentContextService tasks,
            AuthorizationService authorization
    ) {
        this.items = items;
        this.slots = slots;
        this.tasks = tasks;
        this.authorization = authorization;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceItemSummaryView> listSummariesForTask(
            CurrentPrincipal principal, String correlationId, UUID taskId
    ) {
        TaskFulfillmentContext task = tasks.find(principal.tenantId(), taskId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), "Task", taskId.toString(), task.projectId().toString()),
                correlationId);
        if (!slots.resolutionExists(principal.tenantId(), taskId)) {
            throw new BusinessProblem(
                    ProblemCode.TASK_STATE_CONFLICT,
                    "Task evidence slots have not completed reliable resolution");
        }
        return items.listItemSummaries(principal.tenantId(), taskId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceItemSummaryView> listSummariesForTaskOnNetwork(
            CurrentPrincipal principal, String correlationId, UUID taskId, UUID networkId
    ) {
        if (tasks.find(principal.tenantId(), taskId).isEmpty()) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist");
        }
        authorization.require(principal, AuthorizationRequest.networkCapability(
                READ,
                principal.tenantId(),
                "Task",
                taskId.toString(),
                networkId.toString()), correlationId);
        if (!slots.resolutionExists(principal.tenantId(), taskId)) {
            // Network Portal 工作区 soft enrichment：未解析视为该任务无资料项，不抛冲突。
            return List.of();
        }
        return items.listItemSummaries(principal.tenantId(), taskId);
    }
}
