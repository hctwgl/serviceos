package com.serviceos.forms.application;

import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.forms.api.FormSubmissionSummaryView;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FormSubmissionTaskCompletionValidatorTest {

    @Test
    void explainsMissingValidatedFormSubmission() {
        FormSubmissionRepository submissions = mock(FormSubmissionRepository.class);
        TaskFulfillmentContextService tasks = mock(TaskFulfillmentContextService.class);
        ConfigurationService configurations = mock(ConfigurationService.class);
        FormSubmissionTaskCompletionValidator validator = new FormSubmissionTaskCompletionValidator(
                submissions, tasks, configurations);

        UUID taskId = UUID.randomUUID();
        when(tasks.find("t1", taskId)).thenReturn(Optional.of(context(taskId, "survey.form")));
        when(submissions.listSummariesByTask("t1", taskId)).thenReturn(List.of());

        assertThat(validator.explainBlockingReasons("t1", taskId))
                .anyMatch(reason -> reason.contains("表单尚未完成校验提交"))
                .anyMatch(reason -> reason.contains("survey.form"));
    }

    @Test
    void noReasonsWhenValidatedSubmissionExists() {
        FormSubmissionRepository submissions = mock(FormSubmissionRepository.class);
        TaskFulfillmentContextService tasks = mock(TaskFulfillmentContextService.class);
        ConfigurationService configurations = mock(ConfigurationService.class);
        FormSubmissionTaskCompletionValidator validator = new FormSubmissionTaskCompletionValidator(
                submissions, tasks, configurations);

        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(tasks.find("t1", taskId)).thenReturn(Optional.of(context(taskId, "survey.form")));
        when(submissions.listSummariesByTask("t1", taskId)).thenReturn(List.of(
                new FormSubmissionSummaryView(
                        UUID.randomUUID(), taskId, projectId, UUID.randomUUID(), "survey.form",
                        1, "d".repeat(64), "VALIDATED", 0, 0, Instant.now())));

        assertThat(validator.explainBlockingReasons("t1", taskId)).isEmpty();
    }

    private static TaskFulfillmentContext context(UUID taskId, String formRef) {
        return new TaskFulfillmentContext(
                taskId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "b".repeat(64),
                "STAGE_A",
                "SURVEY",
                "HUMAN",
                formRef,
                null,
                null,
                null,
                null,
                "RUNNING",
                "actor",
                false,
                1L);
    }
}
