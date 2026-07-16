package com.serviceos.dispatch.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.dispatch.api.ServiceAssignmentQueryService;
import com.serviceos.dispatch.api.ServiceAssignmentSummary;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** 当前服务责任只读查询；始终由服务端 Task 事实确定 Project Scope。 */
@Service
final class DefaultServiceAssignmentQueryService implements ServiceAssignmentQueryService {
    private static final String READ = "dispatch.read";

    private final ActiveServiceResponsibilityRepository responsibilities;
    private final TaskFulfillmentContextService tasks;
    private final AuthorizationService authorization;

    DefaultServiceAssignmentQueryService(
            ActiveServiceResponsibilityRepository responsibilities,
            TaskFulfillmentContextService tasks,
            AuthorizationService authorization
    ) {
        this.responsibilities = responsibilities;
        this.tasks = tasks;
        this.authorization = authorization;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ServiceAssignmentSummary> findActiveForTask(
            CurrentPrincipal principal, String correlationId, UUID taskId
    ) {
        TaskFulfillmentContext task = tasks.find(principal.tenantId(), taskId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), "Task", taskId.toString(), task.projectId().toString()),
                correlationId);
        return responsibilities.findSummary(principal.tenantId(), taskId);
    }
}
