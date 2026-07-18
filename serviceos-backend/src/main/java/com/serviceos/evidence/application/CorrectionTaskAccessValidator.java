package com.serviceos.evidence.application;

import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.HandlingTaskContextQuery;
import com.serviceos.task.api.HandlingTaskContextView;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * 整改专用状态机门禁：源业务 Task 保持 COMPLETED，独立 handling Task 才能授予后续资料写权限。
 * 该门禁不能复用普通 RUNNING Task 上传校验，否则会诱导重开已完成业务 Task。
 */
@Component
final class CorrectionTaskAccessValidator {
    static final String TASK_TYPE = "evidence.correction";
    private static final Set<String> WRITABLE_CASE_STATUSES = Set.of("IN_PROGRESS", "RESUBMITTED");

    private final CorrectionCaseRepository corrections;
    private final HandlingTaskContextQuery handlingTasks;
    private final TaskFulfillmentContextService sourceTasks;

    CorrectionTaskAccessValidator(
            CorrectionCaseRepository corrections,
            HandlingTaskContextQuery handlingTasks,
            TaskFulfillmentContextService sourceTasks
    ) {
        this.corrections = corrections;
        this.handlingTasks = handlingTasks;
        this.sourceTasks = sourceTasks;
    }

    Access requireWritable(
            CurrentPrincipal principal, UUID correctionCaseId, UUID correctionTaskId, UUID sourceTaskId
    ) {
        CorrectionCaseView correction = corrections.find(principal.tenantId(), correctionCaseId)
                .orElseThrow(() -> notFound("CorrectionCase does not exist"));
        if (!sourceTaskId.equals(correction.taskId())
                || !correctionTaskId.equals(correction.correctionTaskId())
                || !WRITABLE_CASE_STATUSES.contains(correction.status())) {
            throw new BusinessProblem(ProblemCode.CORRECTION_CASE_STATE_CONFLICT,
                    "整改案例、源任务或处理任务状态不一致");
        }
        TaskFulfillmentContext source = sourceTasks.find(principal.tenantId(), sourceTaskId)
                .orElseThrow(() -> notFound("Source Task does not exist"));
        if (!"COMPLETED".equals(source.status())) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                    "整改专用写入只允许已完成的源业务 Task");
        }
        HandlingTaskContextView handling = handlingTasks.findForActor(
                        principal.tenantId(), correctionTaskId, principal.principalId(), TASK_TYPE)
                .orElseThrow(() -> notFound("Correction Task does not exist"));
        if (!correctionCaseId.toString().equals(handling.businessKey())
                || !"RUNNING".equals(handling.status())
                || !principal.principalId().equals(handling.claimedBy())
                || !handling.actorResponsible()) {
            throw new BusinessProblem(ProblemCode.TASK_ASSIGNMENT_CONFLICT,
                    "整改资料写入要求当前主体正在负责 RUNNING correction Task");
        }
        return new Access(correction, source, handling);
    }

    private static BusinessProblem notFound(String message) {
        return new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, message);
    }

    record Access(
            CorrectionCaseView correction,
            TaskFulfillmentContext sourceTask,
            HandlingTaskContextView correctionTask
    ) {
    }
}
