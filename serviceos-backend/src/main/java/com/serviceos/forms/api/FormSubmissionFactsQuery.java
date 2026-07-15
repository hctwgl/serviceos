package com.serviceos.forms.api;

import java.util.Optional;
import java.util.UUID;

/** Evidence 等模块按 tenant 与精确 submissionId 读取已验证条件事实的公开只读端口。 */
public interface FormSubmissionFactsQuery {
    Optional<FormSubmissionFacts> findValidated(String tenantId, UUID submissionId);
}
