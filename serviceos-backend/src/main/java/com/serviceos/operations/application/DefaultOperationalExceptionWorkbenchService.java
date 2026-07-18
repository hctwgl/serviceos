package com.serviceos.operations.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.authorization.api.AuthorizedProjectScope;
import com.serviceos.authorization.api.ProjectScopeAuthorizationService;
import com.serviceos.dispatch.api.ActiveServiceResponsibility;
import com.serviceos.dispatch.api.ActiveServiceResponsibilityService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.operations.api.AcknowledgeOperationalExceptionCommand;
import com.serviceos.operations.api.OperationalExceptionAcknowledgement;
import com.serviceos.operations.api.OperationalExceptionItem;
import com.serviceos.operations.api.OperationalExceptionPage;
import com.serviceos.operations.api.OperationalExceptionQuery;
import com.serviceos.operations.api.OperationalExceptionWorkbenchService;
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

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 运营异常工作台：列表先解析实时项目范围再执行单条范围化 SQL；游标绑定范围与全部筛选。
 */
@Service
final class DefaultOperationalExceptionWorkbenchService implements OperationalExceptionWorkbenchService {
    private static final String READ = "operations.exception.read";
    private static final String ACK = "operations.exception.acknowledge";
    private static final String OPERATION = "operations.exception.acknowledge";
    private static final Set<String> STATUSES = Set.of("OPEN", "ACKNOWLEDGED", "RESOLVED");
    private static final Set<String> SEVERITIES = Set.of("P0", "P1", "P2", "P3");

    private final OperationalExceptionWorkbenchRepository repository;
    private final AuthorizationService authorization;
    private final ProjectScopeAuthorizationService projectScopes;
    private final TaskFulfillmentContextService taskContexts;
    private final ActiveServiceResponsibilityService serviceResponsibilities;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultOperationalExceptionWorkbenchService(
            OperationalExceptionWorkbenchRepository repository,
            AuthorizationService authorization,
            ProjectScopeAuthorizationService projectScopes,
            TaskFulfillmentContextService taskContexts,
            ActiveServiceResponsibilityService serviceResponsibilities,
            IdempotencyService idempotency,
            AuditAppender audit,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.repository = repository;
        this.authorization = authorization;
        this.projectScopes = projectScopes;
        this.taskContexts = taskContexts;
        this.serviceResponsibilities = serviceResponsibilities;
        this.idempotency = idempotency;
        this.audit = audit;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public OperationalExceptionPage list(
            CurrentPrincipal principal, String correlationId, OperationalExceptionQuery query
    ) {
        Objects.requireNonNull(query, "query must not be null");
        String status = normalized(query.status(), STATUSES, "status");
        String severity = normalized(query.severity(), SEVERITIES, "severity");
        String category = optionalCode(query.category(), "category");
        QueryScope scope = query.projectId() == null
                ? collectionScope(principal, correlationId)
                : projectScope(principal, correlationId, query.projectId(), READ);
        Cursor cursor = decode(
                query.cursor(), scope.digest(), query.projectId(), status, category, severity,
                query.workOrderId(), query.taskId());
        List<OperationalExceptionItem> fetched = repository.findPage(
                principal.tenantId(),
                scope.tenantWide(),
                scope.projectIds(),
                query.projectId(),
                status,
                category,
                severity,
                query.workOrderId(),
                query.taskId(),
                cursor == null ? null : cursor.openedAt(),
                cursor == null ? null : cursor.exceptionId(),
                query.limit() + 1);
        boolean canAcknowledge = canAcknowledge(principal, correlationId, "collection", null);
        fetched = fetched.stream().map(item -> withAllowedActions(item, canAcknowledge)).toList();
        boolean more = fetched.size() > query.limit();
        List<OperationalExceptionItem> items = more ? fetched.subList(0, query.limit()) : fetched;
        OperationalExceptionItem last = more ? items.get(items.size() - 1) : null;
        return new OperationalExceptionPage(
                items,
                last == null ? null : encode(
                        scope.digest(), query.projectId(), status, category, severity,
                        query.workOrderId(), query.taskId(), last.openedAt(), last.exceptionId()));
    }

    @Override
    @Transactional(readOnly = true)
    public OperationalExceptionItem get(CurrentPrincipal principal, String correlationId, UUID exceptionId) {
        OperationalExceptionItem item = repository.findById(principal.tenantId(), exceptionId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "OperationalException does not exist"));
        // PROJECT + NETWORK 并入请求，使 Network Portal NETWORK scope operations.exception.read 可匹配（M203）。
        authorizeRead(principal, correlationId, exceptionId, item.projectId(), item.taskId());
        return withAllowedActions(
                item, canAcknowledge(principal, correlationId, exceptionId.toString(), item.projectId()));
    }

    /**
     * 按任务列出运营异常。
     * <p>
     * 事务边界：只读。鉴权先于查询；NETWORK scope 依赖 ACTIVE NETWORK 责任网点。
     */
    @Override
    @Transactional(readOnly = true)
    public List<OperationalExceptionItem> listForTask(
            CurrentPrincipal principal, String correlationId, UUID taskId
    ) {
        Objects.requireNonNull(taskId, "taskId must not be null");
        TaskFulfillmentContext task = taskContexts.find(principal.tenantId(), taskId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
        authorization.require(principal, capabilityRequest(
                        READ, principal.tenantId(), "Task", taskId.toString(),
                        task.projectId(), taskId),
                correlationId);
        return repository.listByTask(principal.tenantId(), taskId);
    }

    @Override
    @Transactional
    public OperationalExceptionAcknowledgement acknowledge(
            CurrentPrincipal principal, CommandMetadata metadata,
            AcknowledgeOperationalExceptionCommand command
    ) {
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        OperationalExceptionItem current = repository.findById(context.tenantId(), command.exceptionId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "OperationalException does not exist"));
        String digest = Sha256.digest(command.exceptionId() + "|" + command.expectedVersion()
                + "|" + (command.note() == null ? "" : command.note()));
        AuthorizationDecision auth = authorizeCapability(
                principal, context.correlationId(), ACK, command.exceptionId().toString(),
                current.projectId());
        IdempotencyDecision decision = idempotency.begin(context, OPERATION, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return repository.findAcknowledgement(context.tenantId(), context.idempotencyKey());
        }

        Instant now = clock.instant();
        if (!repository.acknowledge(
                context.tenantId(), command.exceptionId(), command.expectedVersion(),
                context.actorId(), command.note(), now)) {
            OperationalExceptionItem latest = repository.findById(context.tenantId(), command.exceptionId())
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.RESOURCE_NOT_FOUND, "OperationalException does not exist"));
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT,
                    latest.aggregateVersion() != command.expectedVersion()
                            ? "OperationalException version changed"
                            : "Only an OPEN OperationalException can be acknowledged");
        }

        OperationalExceptionAcknowledgement receipt = new OperationalExceptionAcknowledgement(
                command.exceptionId(), "ACKNOWLEDGED", command.expectedVersion() + 1,
                now, context.actorId());
        repository.saveAcknowledgement(context.tenantId(), context.idempotencyKey(), receipt);
        String payload = serialize(receipt);
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "operations",
                "operational.exception.acknowledged", 1, "OperationalException",
                command.exceptionId().toString(), receipt.aggregateVersion(), context.tenantId(),
                context.correlationId(), context.idempotencyKey(), command.exceptionId().toString(),
                payload, Sha256.digest(payload), now));
        audit.append(new AuditEntry(
                UUID.randomUUID(), context.tenantId(), context.actorId(),
                "OPERATIONAL_EXCEPTION_ACKNOWLEDGE", ACK, "OperationalException",
                command.exceptionId().toString(), "ALLOW", auth.matchedGrantIds(), auth.policyVersion(),
                "SUCCEEDED", null, digest, context.correlationId(), now));
        idempotency.complete(context, OPERATION, command.exceptionId().toString(), Sha256.digest(payload));
        return receipt;
    }

    private QueryScope projectScope(
            CurrentPrincipal principal, String correlationId, UUID projectId, String capability
    ) {
        authorization.require(principal, AuthorizationRequest.projectCapability(
                capability, principal.tenantId(), "OperationalException", "collection",
                projectId.toString()), correlationId);
        return new QueryScope(false, List.of(projectId), Sha256.digest("PROJECTS:" + projectId));
    }

    private QueryScope collectionScope(CurrentPrincipal principal, String correlationId) {
        AuthorizedProjectScope scope =
                projectScopes.require(principal, READ, "OperationalException", correlationId);
        return new QueryScope(
                scope.tenantWide(),
                scope.projectIds().stream()
                        .sorted(Comparator.comparing(UUID::toString))
                        .toList(),
                scope.scopeDigest());
    }

    private void authorizeRead(
            CurrentPrincipal principal,
            String correlationId,
            UUID exceptionId,
            UUID projectId,
            UUID taskId
    ) {
        authorization.require(principal, capabilityRequest(
                        READ, principal.tenantId(), "OperationalException", exceptionId.toString(),
                        projectId, taskId),
                correlationId);
    }

    private AuthorizationDecision authorizeCapability(
            CurrentPrincipal principal,
            String correlationId,
            String capability,
            String resourceId,
            UUID projectId
    ) {
        if (projectId != null) {
            return authorization.require(principal, AuthorizationRequest.projectCapability(
                    capability, principal.tenantId(), "OperationalException", resourceId,
                    projectId.toString()), correlationId);
        }
        return authorization.require(principal, AuthorizationRequest.tenantCapability(
                capability, principal.tenantId(), "OperationalException", resourceId), correlationId);
    }

    /**
     * 同时携带 projectId 与 ACTIVE NETWORK 责任网点，使 PROJECT/NETWORK RoleGrant 均可匹配。
     */
    private AuthorizationRequest capabilityRequest(
            String capability,
            String tenantId,
            String resourceType,
            String resourceId,
            UUID projectId,
            UUID taskId
    ) {
        String networkId = taskId == null
                ? null
                : serviceResponsibilities.find(tenantId, taskId)
                        .map(ActiveServiceResponsibility::networkId)
                        .orElse(null);
        return new AuthorizationRequest(
                capability, tenantId, resourceType, resourceId,
                projectId == null ? null : projectId.toString(), null, null, networkId);
    }

    private boolean canAcknowledge(
            CurrentPrincipal principal, String correlationId, String id, UUID projectId
    ) {
        AuthorizationRequest request = projectId == null
                ? AuthorizationRequest.tenantCapability(
                        ACK, principal.tenantId(), "OperationalException", id)
                : AuthorizationRequest.projectCapability(
                        ACK, principal.tenantId(), "OperationalException", id, projectId.toString());
        return authorization.authorize(principal, request, correlationId)
                .effect() == AuthorizationDecision.Effect.ALLOW;
    }

    private static OperationalExceptionItem withAllowedActions(
            OperationalExceptionItem item, boolean canAcknowledge
    ) {
        List<String> actions = canAcknowledge && "OPEN".equals(item.status())
                ? List.of("ACKNOWLEDGE") : List.of();
        return new OperationalExceptionItem(
                item.exceptionId(), item.projectId(), item.sourceType(), item.sourceId(),
                item.sourceAttemptId(), item.sourceTaskType(), item.category(), item.severity(),
                item.errorCode(), item.status(), item.workOrderId(), item.taskId(),
                item.handlingTaskId(), item.occurrenceCount(), item.aggregateVersion(),
                item.openedAt(), item.lastDetectedAt(), item.acknowledgedAt(),
                item.acknowledgedBy(), item.acknowledgementNote(), item.resolvedAt(),
                item.resolutionCode(), actions);
    }

    private static String normalized(String value, Set<String> allowed, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, name + " is invalid");
        }
        return normalized;
    }

    private static String optionalCode(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_]{1,80}")) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, name + " is invalid");
        }
        return normalized;
    }

    private static String encode(
            String scopeDigest,
            UUID projectId,
            String status,
            String category,
            String severity,
            UUID workOrderId,
            UUID taskId,
            Instant openedAt,
            UUID exceptionId
    ) {
        String raw = String.join(
                "|",
                scopeDigest,
                nullable(projectId),
                nullable(status),
                nullable(category),
                nullable(severity),
                nullable(workOrderId),
                nullable(taskId),
                openedAt.toString(),
                exceptionId.toString());
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decode(
            String value,
            String scopeDigest,
            UUID projectId,
            String status,
            String category,
            String severity,
            UUID workOrderId,
            UUID taskId
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 9
                    || !scopeDigest.equals(parts[0])
                    || !nullable(projectId).equals(parts[1])
                    || !nullable(status).equals(parts[2])
                    || !nullable(category).equals(parts[3])
                    || !nullable(severity).equals(parts[4])
                    || !nullable(workOrderId).equals(parts[5])
                    || !nullable(taskId).equals(parts[6])) {
                throw new IllegalArgumentException();
            }
            return new Cursor(Instant.parse(parts[7]), UUID.fromString(parts[8]));
        } catch (RuntimeException exception) {
            throw new BusinessProblem(
                    ProblemCode.VALIDATION_FAILED,
                    "cursor is invalid for the requested exception scope");
        }
    }

    private static String nullable(Object value) {
        return value == null ? "-" : value.toString();
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("OperationalException serialization failed", exception);
        }
    }

    private record Cursor(Instant openedAt, UUID exceptionId) {
    }

    private record QueryScope(boolean tenantWide, List<UUID> projectIds, String digest) {
        private QueryScope {
            projectIds = List.copyOf(projectIds);
        }
    }
}
