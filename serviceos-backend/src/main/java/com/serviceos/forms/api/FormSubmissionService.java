package com.serviceos.forms.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.UUID;

/** FormSubmission 写入与读取公开边界；提交不隐式完成 Task。 */
public interface FormSubmissionService {
    FormSubmissionView submit(CurrentPrincipal principal, CommandMetadata metadata, SubmitFormCommand command);
    FormSubmissionView get(CurrentPrincipal principal, String correlationId, UUID submissionId);
}
