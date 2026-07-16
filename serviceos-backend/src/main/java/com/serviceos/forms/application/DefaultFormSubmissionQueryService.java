package com.serviceos.forms.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.forms.api.FormSubmissionQueryService;
import com.serviceos.forms.api.FormSubmissionSummaryView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** FormSubmission 元数据查询；不加载 values 或校验消息。 */
@Service
final class DefaultFormSubmissionQueryService implements FormSubmissionQueryService {
    private static final String READ = "form.read";

    private final FormSubmissionRepository submissions;
    private final TaskFulfillmentContextService tasks;
    private final AuthorizationService authorization;

    DefaultFormSubmissionQueryService(
            FormSubmissionRepository submissions,
            TaskFulfillmentContextService tasks,
            AuthorizationService authorization
    ) {
        this.submissions = submissions;
        this.tasks = tasks;
        this.authorization = authorization;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FormSubmissionSummaryView> listForTask(
            CurrentPrincipal principal, String correlationId, UUID taskId
    ) {
        TaskFulfillmentContext task = tasks.find(principal.tenantId(), taskId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), "Task", taskId.toString(), task.projectId().toString()),
                correlationId);
        return submissions.listSummariesByTask(principal.tenantId(), taskId);
    }
}
