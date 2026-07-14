package com.serviceos.operations.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
final class DefaultOperationalExceptionWorkbenchService implements OperationalExceptionWorkbenchService {
    private static final String READ = "operations.exception.read";
    private static final String ACK = "operations.exception.acknowledge";
    private static final String OPERATION = "operations.exception.acknowledge";
    private static final Set<String> STATUSES = Set.of("OPEN", "ACKNOWLEDGED", "RESOLVED");
    private static final Set<String> SEVERITIES = Set.of("P0", "P1", "P2", "P3");

    private final OperationalExceptionWorkbenchRepository repository;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultOperationalExceptionWorkbenchService(
            OperationalExceptionWorkbenchRepository repository, AuthorizationService authorization,
            IdempotencyService idempotency, AuditAppender audit, OutboxAppender outbox,
            ObjectMapper objectMapper, Clock clock
    ) {
        this.repository = repository;
        this.authorization = authorization;
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
        requireRead(principal, correlationId, "OperationalException", "collection");
        String status = normalized(query.status(), STATUSES, "status");
        String severity = normalized(query.severity(), SEVERITIES, "severity");
        String category = optionalCode(query.category(), "category");
        Cursor cursor = decode(query.cursor());
        List<OperationalExceptionItem> fetched = repository.findPage(
                principal.tenantId(), status, category, severity, query.workOrderId(), query.taskId(),
                cursor == null ? null : cursor.openedAt(), cursor == null ? null : cursor.exceptionId(),
                query.limit() + 1);
        boolean canAcknowledge = canAcknowledge(principal, correlationId, "collection");
        fetched = fetched.stream().map(item -> withAllowedActions(item, canAcknowledge)).toList();
        boolean more = fetched.size() > query.limit();
        List<OperationalExceptionItem> items = more ? fetched.subList(0, query.limit()) : fetched;
        OperationalExceptionItem last = more ? items.get(items.size() - 1) : null;
        return new OperationalExceptionPage(items, last == null ? null : encode(last.openedAt(), last.exceptionId()));
    }

    @Override
    @Transactional(readOnly = true)
    public OperationalExceptionItem get(CurrentPrincipal principal, String correlationId, UUID exceptionId) {
        requireRead(principal, correlationId, "OperationalException", exceptionId.toString());
        OperationalExceptionItem item = repository.findById(principal.tenantId(), exceptionId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "OperationalException does not exist"));
        return withAllowedActions(item, canAcknowledge(principal, correlationId, exceptionId.toString()));
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
        String digest = Sha256.digest(command.exceptionId() + "|" + command.expectedVersion()
                + "|" + (command.note() == null ? "" : command.note()));
        AuthorizationDecision auth = authorization.require(
                principal,
                AuthorizationRequest.tenantCapability(
                        ACK, context.tenantId(), "OperationalException", command.exceptionId().toString()),
                context.correlationId());
        IdempotencyDecision decision = idempotency.begin(context, OPERATION, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return repository.findAcknowledgement(context.tenantId(), context.idempotencyKey());
        }

        Instant now = clock.instant();
        if (!repository.acknowledge(
                context.tenantId(), command.exceptionId(), command.expectedVersion(),
                context.actorId(), command.note(), now)) {
            OperationalExceptionItem current = repository.findById(context.tenantId(), command.exceptionId())
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.RESOURCE_NOT_FOUND, "OperationalException does not exist"));
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT,
                    current.aggregateVersion() != command.expectedVersion()
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

    private void requireRead(CurrentPrincipal principal, String correlationId, String type, String id) {
        authorization.require(principal,
                AuthorizationRequest.tenantCapability(READ, principal.tenantId(), type, id), correlationId);
    }

    private boolean canAcknowledge(CurrentPrincipal principal, String correlationId, String id) {
        return authorization.authorize(principal,
                AuthorizationRequest.tenantCapability(
                        ACK, principal.tenantId(), "OperationalException", id), correlationId)
                .effect() == AuthorizationDecision.Effect.ALLOW;
    }

    private static OperationalExceptionItem withAllowedActions(
            OperationalExceptionItem item, boolean canAcknowledge
    ) {
        List<String> actions = canAcknowledge && "OPEN".equals(item.status())
                ? List.of("ACKNOWLEDGE") : List.of();
        return new OperationalExceptionItem(
                item.exceptionId(), item.sourceType(), item.sourceId(), item.sourceAttemptId(),
                item.sourceTaskType(), item.category(), item.severity(), item.errorCode(), item.status(),
                item.workOrderId(), item.taskId(), item.handlingTaskId(), item.occurrenceCount(),
                item.aggregateVersion(), item.openedAt(), item.lastDetectedAt(), item.acknowledgedAt(),
                item.acknowledgedBy(), item.acknowledgementNote(), item.resolvedAt(),
                item.resolutionCode(), actions);
    }

    private static String normalized(String value, Set<String> allowed, String name) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase();
        if (!allowed.contains(normalized)) throw new IllegalArgumentException(name + " is invalid");
        return normalized;
    }

    private static String optionalCode(String value, String name) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase();
        if (!normalized.matches("[A-Z0-9_]{1,80}")) throw new IllegalArgumentException(name + " is invalid");
        return normalized;
    }

    private static String encode(Instant openedAt, UUID exceptionId) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (openedAt + "|" + exceptionId).getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decode(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("cursor is invalid", exception);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("OperationalException serialization failed", exception);
        }
    }

    private record Cursor(Instant openedAt, UUID exceptionId) {}
}
