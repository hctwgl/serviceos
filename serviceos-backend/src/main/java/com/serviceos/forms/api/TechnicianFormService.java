package com.serviceos.forms.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.List;
import java.util.UUID;

/** Technician Portal 当前责任任务的冻结表单查询与在线不可变提交边界。 */
public interface TechnicianFormService {
    List<TaskFormDefinition> listForTask(
            CurrentPrincipal principal, String correlationId, String technicianContextHeader, UUID taskId);

    FormSubmissionView submit(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String technicianContextHeader,
            SubmitFormCommand command);
}
