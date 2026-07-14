package com.serviceos.evidence.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.evidence.api.EvidenceSlotQueryService;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** EvidenceSlot 只读查询；未完成可靠解析时返回冲突，不能把暂时空结果伪装成无需资料。 */
@Service
final class DefaultEvidenceSlotQueryService implements EvidenceSlotQueryService {
    private static final String READ = "evidence.read";

    private final EvidenceSlotRepository repository;
    private final TaskFulfillmentContextService tasks;
    private final AuthorizationService authorization;

    DefaultEvidenceSlotQueryService(
            EvidenceSlotRepository repository,
            TaskFulfillmentContextService tasks,
            AuthorizationService authorization
    ) {
        this.repository = repository;
        this.tasks = tasks;
        this.authorization = authorization;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceSlotView> listForTask(
            CurrentPrincipal principal, String correlationId, UUID taskId
    ) {
        TaskFulfillmentContext task = tasks.find(principal.tenantId(), taskId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), "Task", taskId.toString(), task.projectId().toString()),
                correlationId);
        if (!repository.resolutionExists(principal.tenantId(), taskId)) {
            throw new BusinessProblem(
                    ProblemCode.TASK_STATE_CONFLICT,
                    "Task evidence slots have not completed reliable resolution");
        }
        return repository.listSlots(principal.tenantId(), taskId);
    }
}
