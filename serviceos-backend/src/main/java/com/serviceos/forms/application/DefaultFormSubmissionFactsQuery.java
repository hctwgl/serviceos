package com.serviceos.forms.application;

import com.serviceos.forms.api.FormSubmissionFacts;
import com.serviceos.forms.api.FormSubmissionFactsQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** 只返回精确 VALIDATED submission；INVALID 与跨租户请求都表现为空，不猜测替代版本。 */
@Service
final class DefaultFormSubmissionFactsQuery implements FormSubmissionFactsQuery {
    private final FormSubmissionRepository repository;

    DefaultFormSubmissionFactsQuery(FormSubmissionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FormSubmissionFacts> findValidated(String tenantId, UUID submissionId) {
        return repository.find(tenantId, submissionId)
                .filter(view -> "VALIDATED".equals(view.validationStatus()))
                .map(view -> new FormSubmissionFacts(
                        view.submissionId(), view.taskId(), view.projectId(), view.formVersionId(),
                        view.formKey(), view.submissionVersion(), view.valuesJson(), view.contentDigest()));
    }
}
