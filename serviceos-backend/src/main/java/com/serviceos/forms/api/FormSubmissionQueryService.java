package com.serviceos.forms.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.List;
import java.util.UUID;

/** FormSubmission 安全摘要查询边界。 */
public interface FormSubmissionQueryService {
    List<FormSubmissionSummaryView> listForTask(
            CurrentPrincipal principal, String correlationId, UUID taskId);

    /**
     * M222 Network Portal：以 NETWORK {@code form.read} 鉴权后按任务列出提交摘要。
     * 调用方须已将 taskId 收敛到本网点 ACTIVE 责任集合。
     */
    List<FormSubmissionSummaryView> listForTaskOnNetwork(
            CurrentPrincipal principal, String correlationId, UUID taskId, UUID networkId);
}
