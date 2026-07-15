package com.serviceos.project.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.project.api.CreateProjectCommand;
import com.serviceos.project.api.ProjectCommandService;
import com.serviceos.project.api.ProjectView;
import com.serviceos.project.domain.Project;
import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * 创建项目的首条工程纵向切片。
 *
 * <p>事务顺序严格遵循 ARCH-19/20：幂等抢占 → 聚合写入 → 审计 → Outbox → 幂等结果。
 * 任何一步失败都会回滚全部写入，网络发布不在本事务内发生。</p>
 */
@Service
final class DefaultProjectCommandService implements ProjectCommandService {
    static final String OPERATION_TYPE = "project.create";

    private static final ObjectMapper CANONICAL_JSON = JsonMapper.builder()
            .findAndAddModules()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .build();

    private final ProjectRepository projects;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final Clock clock;

    DefaultProjectCommandService(
            ProjectRepository projects,
            AuthorizationService authorization,
            IdempotencyService idempotency,
            AuditAppender audit,
            OutboxAppender outbox,
            Clock clock
    ) {
        this.projects = projects;
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProjectView create(CurrentPrincipal principal, CommandMetadata metadata, CreateProjectCommand command) {
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        AuthorizationDecision authorizationDecision = authorization.require(
                principal,
                AuthorizationRequest.tenantCapability(
                        "project.create", context.tenantId(), "Project", command.code()),
                context.correlationId());

        String requestDigest = Sha256.digest(canonicalJson(command));
        IdempotencyDecision decision = idempotency.begin(context, OPERATION_TYPE, requestDigest);

        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            UUID projectId = UUID.fromString(decision.resourceId().orElseThrow());
            return projects.findById(context.tenantId(), projectId)
                    .map(Project::toView)
                    .orElseThrow(() -> new IllegalStateException(
                            "Idempotency result references a missing project"));
        }

        Instant now = clock.instant();
        Project project = Project.create(context.tenantId(), command, UUID.randomUUID(), now);
        projects.insert(project);
        projects.insertRegionBindings(project, context.actorId());

        String eventPayload = canonicalJson(new ProjectCreatedPayload(
                project.id(), project.tenantId(), project.code(), project.clientId(),
                project.name(), project.startsOn(), project.endsOn(), project.regionCodes(), project.status().name(),
                project.version(), project.createdAt()));
        String payloadDigest = Sha256.digest(eventPayload);
        UUID outboxId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        audit.append(new AuditEntry(
                UUID.randomUUID(), context.tenantId(), context.actorId(), "PROJECT_CREATED", "project.create",
                "Project", project.id().toString(), "ALLOW", authorizationDecision.matchedGrantIds(),
                authorizationDecision.policyVersion(), "SUCCEEDED", null, requestDigest,
                context.correlationId(), now));
        outbox.append(new OutboxEvent(
                outboxId, eventId, "project", "project.created", 2,
                "Project", project.id().toString(), project.version(), context.tenantId(),
                context.correlationId(), context.idempotencyKey(), project.id().toString(),
                eventPayload, payloadDigest, now));

        ProjectView result = project.toView();
        idempotency.complete(context, OPERATION_TYPE, project.id().toString(), Sha256.digest(canonicalJson(result)));
        return result;
    }

    private static String canonicalJson(Object value) {
        try {
            return CANONICAL_JSON.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Command cannot be serialized", exception);
        }
    }

    private record ProjectCreatedPayload(
            UUID projectId,
            String tenantId,
            String code,
            String clientId,
            String name,
            java.time.LocalDate startsOn,
            java.time.LocalDate endsOn,
            java.util.List<String> regionCodes,
            String status,
            long aggregateVersion,
            Instant occurredAt
    ) {
    }
}
