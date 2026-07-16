package com.serviceos.forms.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.List;
import java.util.UUID;

/** FormSubmission 安全摘要查询边界。 */
public interface FormSubmissionQueryService {
    List<FormSubmissionSummaryView> listForTask(
            CurrentPrincipal principal, String correlationId, UUID taskId);
}
