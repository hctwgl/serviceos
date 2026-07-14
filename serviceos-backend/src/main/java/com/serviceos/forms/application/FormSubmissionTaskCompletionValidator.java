package com.serviceos.forms.application;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.forms.api.FormSubmissionView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.CompleteHumanTaskCommand;
import com.serviceos.task.api.HumanTaskCompletionValidator;
import com.serviceos.task.api.InputVersionRef;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Task 完成只能引用同一 Task 冻结 FormVersion 的权威 VALIDATED submission。 */
@Component
final class FormSubmissionTaskCompletionValidator implements HumanTaskCompletionValidator {
    private static final String REFERENCE_PREFIX = "form-submission://";

    private final FormSubmissionRepository submissions;
    private final TaskFulfillmentContextService tasks;
    private final ConfigurationService configurations;

    FormSubmissionTaskCompletionValidator(
            FormSubmissionRepository submissions,
            TaskFulfillmentContextService tasks,
            ConfigurationService configurations
    ) {
        this.submissions = submissions;
        this.tasks = tasks;
        this.configurations = configurations;
    }

    @Override
    public void validate(CurrentPrincipal principal, CompleteHumanTaskCommand command) {
        TaskFulfillmentContext task = tasks.find(principal.tenantId(), command.taskId())
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
        if (task.formRef() == null) {
            return;
        }
        if (!command.inputVersionRefs().isEmpty()
                && command.inputVersionRefs().stream()
                .noneMatch(ref -> InputVersionRef.FORM_SUBMISSION.equals(ref.kind()))) {
            throw notValidated();
        }

        UUID submissionId = submissionId(command.resultRef());
        FormSubmissionView submission = submissions.find(principal.tenantId(), submissionId)
                .orElseThrow(FormSubmissionTaskCompletionValidator::notValidated);
        ConfigurationAssetDefinition lockedForm = lockedForm(principal.tenantId(), task);
        if (!"VALIDATED".equals(submission.validationStatus())
                || !command.taskId().equals(submission.taskId())
                || !task.projectId().equals(submission.projectId())
                || !task.formRef().equals(submission.formKey())
                || !lockedForm.versionId().equals(submission.formVersionId())
                || !submission.contentDigest().equals(command.resultDigest())) {
            throw notValidated();
        }

        command.inputVersionRefs().stream()
                .filter(ref -> InputVersionRef.FORM_SUBMISSION.equals(ref.kind()))
                .forEach(ref -> {
                    if (!command.resultRef().equals(ref.ref())
                            || !command.resultDigest().equals(ref.digest())) {
                        throw notValidated();
                    }
                });
    }

    private ConfigurationAssetDefinition lockedForm(String tenantId, TaskFulfillmentContext task) {
        List<ConfigurationAssetDefinition> matches = configurations.listBundleAssets(
                        tenantId, task.configurationBundleId(), task.configurationBundleDigest(),
                        ConfigurationAssetType.FORM).stream()
                .filter(asset -> asset.assetKey().equals(task.formRef())).toList();
        if (matches.size() != 1) {
            throw new BusinessProblem(ProblemCode.FORM_VERSION_CONFLICT,
                    "Task formRef does not resolve to exactly one locked FormVersion");
        }
        return matches.getFirst();
    }

    private static UUID submissionId(String resultRef) {
        if (!resultRef.startsWith(REFERENCE_PREFIX)) {
            throw notValidated();
        }
        try {
            return UUID.fromString(resultRef.substring(REFERENCE_PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            throw notValidated();
        }
    }

    private static BusinessProblem notValidated() {
        return new BusinessProblem(ProblemCode.FORM_SUBMISSION_NOT_VALIDATED,
                "Task completion requires an exact VALIDATED FormSubmission reference and content digest");
    }
}
