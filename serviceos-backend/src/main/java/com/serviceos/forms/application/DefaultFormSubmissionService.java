package com.serviceos.forms.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.forms.api.FormSubmissionService;
import com.serviceos.forms.api.FormSubmissionView;
import com.serviceos.forms.api.SubmitFormCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** FormSubmission、验证、审计、Outbox 与幂等结果在同一 PostgreSQL 事务提交。 */
@Service
final class DefaultFormSubmissionService implements FormSubmissionService {
    private static final String SUBMIT = "form.submit";
    private static final String READ = "form.read";
    private static final String OPERATION = "form.submit";

    private final FormSubmissionRepository repository;
    private final TaskFulfillmentContextService tasks;
    private final ConfigurationService configurations;
    private final FormValueValidator validator;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultFormSubmissionService(
            FormSubmissionRepository repository, TaskFulfillmentContextService tasks,
            ConfigurationService configurations, FormValueValidator validator,
            AuthorizationService authorization, IdempotencyService idempotency,
            AuditAppender audit, OutboxAppender outbox, ObjectMapper objectMapper, Clock clock
    ) {
        this.repository = repository;
        this.tasks = tasks;
        this.configurations = configurations;
        this.validator = validator;
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.audit = audit;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public FormSubmissionView submit(
            CurrentPrincipal principal, CommandMetadata metadata, SubmitFormCommand command
    ) {
        TaskFulfillmentContext task = task(principal.tenantId(), command.taskId());
        validateExecutableTask(principal, task);
        AuthorizationDecision auth = authorization.require(principal,
                AuthorizationRequest.projectCapability(SUBMIT, principal.tenantId(), "Task",
                        task.taskId().toString(), task.projectId().toString()), metadata.correlationId());
        ConfigurationAssetDefinition form = lockedForm(principal.tenantId(), task);
        if (!form.versionId().equals(command.formVersionId())) {
            throw new BusinessProblem(ProblemCode.FORM_VERSION_CONFLICT,
                    "formVersionId does not match the Task locked FormVersion");
        }
        if (command.prefillVersion() != null) {
            throw new BusinessProblem(ProblemCode.FORM_RUNTIME_UNSUPPORTED,
                    "Prefill conflict policy is not approved for execution");
        }

        FormValueValidator.ValidationResult validation =
                validator.validate(form.definitionJson(), command.valuesJson());
        String requestDigest = Sha256.digest(command.taskId() + "|" + command.formVersionId()
                + "|" + validation.normalizedValuesJson() + "|" + command.prefillVersion());
        CommandContext context = context(principal, metadata);
        IdempotencyDecision decision = idempotency.begin(context, OPERATION, requestDigest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return repository.findResult(context.tenantId(), OPERATION, context.idempotencyKey());
        }
        if (!repository.lockExecutableTask(context.tenantId(), task.taskId(), context.actorId())) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                    "Task execution eligibility changed before FormSubmission commit");
        }

        Instant now = clock.instant();
        UUID submissionId = UUID.randomUUID();
        String contentDigest = Sha256.digest(validation.normalizedValuesJson());
        FormSubmissionView submission = new FormSubmissionView(
                submissionId, task.taskId(), task.projectId(), form.versionId(), form.assetKey(),
                repository.nextVersion(context.tenantId(), task.taskId(), form.versionId()),
                validation.normalizedValuesJson(), contentDigest, validation.status(),
                validation.errors(), validation.warnings(), command.prefillVersion(),
                context.actorId(), now);
        repository.insert(context.tenantId(), submission);
        repository.insertValidation(UUID.randomUUID(), context.tenantId(), submission,
                FormValueValidator.VERSION, contentDigest);
        repository.saveResult(context.tenantId(), OPERATION, context.idempotencyKey(), submissionId);

        String payload = serialize(new FormSubmittedPayload(
                submissionId, task.taskId(), task.projectId(), form.versionId(), form.assetKey(),
                submission.submissionVersion(), contentDigest, validation.status(),
                validation.errors().size(), validation.warnings().size(), now, "form.submitted"));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "forms", "form.submitted", 1,
                "FormSubmission", submissionId.toString(), 1,
                context.tenantId(), context.correlationId(), context.idempotencyKey(),
                task.taskId().toString(), payload, Sha256.digest(payload), now));
        audit.append(new AuditEntry(
                UUID.randomUUID(), context.tenantId(), context.actorId(), "FORM_SUBMITTED", SUBMIT,
                "FormSubmission", submissionId.toString(), "ALLOW", auth.matchedGrantIds(),
                auth.policyVersion(), validation.status(), null, requestDigest,
                context.correlationId(), now));
        idempotency.complete(context, OPERATION, submissionId.toString(),
                Sha256.digest(serialize(submission)));
        return submission;
    }

    @Override
    @Transactional(readOnly = true)
    public FormSubmissionView get(CurrentPrincipal principal, String correlationId, UUID submissionId) {
        FormSubmissionView submission = repository.find(principal.tenantId(), submissionId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "FormSubmission does not exist"));
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), "FormSubmission", submissionId.toString(),
                submission.projectId().toString()), correlationId);
        return submission;
    }

    private ConfigurationAssetDefinition lockedForm(String tenantId, TaskFulfillmentContext task) {
        if (task.formRef() == null) {
            throw new BusinessProblem(ProblemCode.FORM_VERSION_CONFLICT, "Task does not lock a FormVersion");
        }
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

    private static void validateExecutableTask(CurrentPrincipal principal, TaskFulfillmentContext task) {
        if (!"HUMAN".equals(task.taskKind()) || !"RUNNING".equals(task.status())) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                    "Form submission requires a RUNNING HUMAN Task");
        }
        if (task.executionGuarded()) {
            throw new BusinessProblem(ProblemCode.TASK_EXECUTION_GUARDED,
                    "Form submission is disabled while a Task execution guard is ACTIVE");
        }
        if (!principal.principalId().equals(task.responsiblePrincipalId())) {
            throw new BusinessProblem(ProblemCode.TECHNICIAN_ASSIGNMENT_CHANGED,
                    "Task no longer belongs to this technician");
        }
    }

    private TaskFulfillmentContext task(String tenantId, UUID taskId) {
        return tasks.find(tenantId, taskId).orElseThrow(() ->
                new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
    }

    private static CommandContext context(CurrentPrincipal principal, CommandMetadata metadata) {
        return new CommandContext(principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
    }

    private String serialize(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JacksonException exception) {
            throw new IllegalStateException("Form submission serialization failed", exception);
        }
    }

    private record FormSubmittedPayload(
            UUID submissionId, UUID taskId, UUID projectId, UUID formVersionId, String formKey,
            int submissionVersion, String contentDigest, String validationStatus,
            int errorCount, int warningCount, Instant occurredAt, String eventType) { }
}
