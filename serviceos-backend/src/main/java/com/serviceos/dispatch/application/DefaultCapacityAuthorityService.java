package com.serviceos.dispatch.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.dispatch.api.CapacityAuthorityService;
import com.serviceos.dispatch.api.CapacityConfiguredPayload;
import com.serviceos.dispatch.api.CapacityCounterReceipt;
import com.serviceos.dispatch.api.ConfigureCapacityCommand;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/** 容量硬门禁的权威配置服务；占用量只能由 reservation 事务修改。 */
@Service
final class DefaultCapacityAuthorityService implements CapacityAuthorityService {
    private static final String OPERATION = "dispatch.capacity.configure";
    private static final String CAPABILITY = "dispatch.capacity.configure";

    private final JdbcClient jdbc;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultCapacityAuthorityService(
            JdbcClient jdbc,
            AuthorizationService authorization,
            IdempotencyService idempotency,
            AuditAppender audit,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.audit = audit;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CapacityCounterReceipt configure(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ConfigureCapacityCommand command
    ) {
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        String digest = Sha256.digest(command.responsibilityLevel() + "|" + command.assigneeId()
                + "|" + command.businessType() + "|" + command.maxUnits()
                + "|" + command.expectedVersion());
        AuthorizationDecision authorizationDecision = authorization.require(
                principal,
                AuthorizationRequest.tenantCapability(
                        CAPABILITY, context.tenantId(), "CapacityCounter",
                        command.responsibilityLevel() + ":" + command.assigneeId()
                                + ":" + command.businessType()),
                context.correlationId());
        IdempotencyDecision decision = idempotency.begin(context, OPERATION, digest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return frozenReceipt(context);
        }

        Instant now = clock.instant();
        CapacityCounterReceipt receipt = command.expectedVersion() == 0
                ? create(context, command, now)
                : update(context, command, now);
        insertFrozenReceipt(context, receipt);
        appendEvent(context, receipt);
        audit.append(new AuditEntry(
                UUID.randomUUID(), context.tenantId(), context.actorId(),
                "DISPATCH_CAPACITY_CONFIGURE", CAPABILITY, "CapacityCounter",
                receipt.capacityCounterId().toString(), "ALLOW",
                authorizationDecision.matchedGrantIds(), authorizationDecision.policyVersion(),
                "SUCCEEDED", null, digest, context.correlationId(), now));
        idempotency.complete(context, OPERATION, receipt.capacityCounterId().toString(),
                Sha256.digest(serialize(receipt)));
        return receipt;
    }

    private CapacityCounterReceipt create(
            CommandContext context, ConfigureCapacityCommand command, Instant now) {
        UUID counterId = UUID.randomUUID();
        try {
            jdbc.sql("""
                            INSERT INTO dsp_capacity_counter (
                                capacity_counter_id, tenant_id, responsibility_level,
                                assignee_id, business_type, max_units, occupied_units,
                                version, updated_by, updated_at
                            ) VALUES (
                                :counterId, :tenantId, :level,
                                :assigneeId, :businessType, :maxUnits, 0,
                                1, :actorId, :now
                            )
                            """)
                    .param("counterId", counterId).param("tenantId", context.tenantId())
                    .param("level", command.responsibilityLevel().name())
                    .param("assigneeId", command.assigneeId())
                    .param("businessType", command.businessType()).param("maxUnits", command.maxUnits())
                    .param("actorId", context.actorId()).param("now", timestamptz(now)).update();
        } catch (DuplicateKeyException exception) {
            throw new BusinessProblem(
                    ProblemCode.VERSION_CONFLICT,
                    "Capacity counter already exists and must be changed with its current version");
        }
        return new CapacityCounterReceipt(
                counterId, command.responsibilityLevel(), command.assigneeId(),
                command.businessType(), command.maxUnits(), 0, 1, now);
    }

    private CapacityCounterReceipt update(
            CommandContext context, ConfigureCapacityCommand command, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE dsp_capacity_counter
                           SET max_units = :maxUnits, version = version + 1,
                               updated_by = :actorId, updated_at = :now
                         WHERE tenant_id = :tenantId AND responsibility_level = :level
                           AND assignee_id = :assigneeId AND business_type = :businessType
                           AND version = :expectedVersion AND occupied_units <= :maxUnits
                        """)
                .param("maxUnits", command.maxUnits()).param("actorId", context.actorId())
                .param("now", timestamptz(now)).param("tenantId", context.tenantId())
                .param("level", command.responsibilityLevel().name())
                .param("assigneeId", command.assigneeId())
                .param("businessType", command.businessType())
                .param("expectedVersion", command.expectedVersion()).update();
        if (updated != 1) {
            CapacityState state = findState(context.tenantId(), command);
            if (state.version() != command.expectedVersion()) {
                throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "Capacity counter version changed");
            }
            throw new BusinessProblem(
                    ProblemCode.DISPATCH_CAPACITY_CONFLICT,
                    "Capacity limit cannot be reduced below the current occupied units");
        }
        CapacityState state = findState(context.tenantId(), command);
        return new CapacityCounterReceipt(
                state.capacityCounterId(), command.responsibilityLevel(), command.assigneeId(),
                command.businessType(), command.maxUnits(), state.occupiedUnits(),
                state.version(), now);
    }

    private CapacityState findState(String tenantId, ConfigureCapacityCommand command) {
        return jdbc.sql("""
                        SELECT capacity_counter_id, occupied_units, version
                          FROM dsp_capacity_counter
                         WHERE tenant_id = :tenantId AND responsibility_level = :level
                           AND assignee_id = :assigneeId AND business_type = :businessType
                        """)
                .param("tenantId", tenantId).param("level", command.responsibilityLevel().name())
                .param("assigneeId", command.assigneeId())
                .param("businessType", command.businessType())
                .query(CapacityState.class).optional()
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Capacity counter does not exist"));
    }

    private void insertFrozenReceipt(CommandContext context, CapacityCounterReceipt receipt) {
        jdbc.sql("""
                        INSERT INTO dsp_capacity_command_result (
                            tenant_id, operation_type, idempotency_key, capacity_counter_id,
                            responsibility_level, assignee_id, business_type, max_units,
                            occupied_units, counter_version, occurred_at
                        ) VALUES (
                            :tenantId, :operation, :idempotencyKey, :counterId,
                            :level, :assigneeId, :businessType, :maxUnits,
                            :occupiedUnits, :version, :occurredAt
                        )
                        """)
                .param("tenantId", context.tenantId()).param("operation", OPERATION)
                .param("idempotencyKey", context.idempotencyKey())
                .param("counterId", receipt.capacityCounterId())
                .param("level", receipt.responsibilityLevel().name())
                .param("assigneeId", receipt.assigneeId()).param("businessType", receipt.businessType())
                .param("maxUnits", receipt.maxUnits()).param("occupiedUnits", receipt.occupiedUnits())
                .param("version", receipt.version()).param("occurredAt", timestamptz(receipt.occurredAt()))
                .update();
    }

    private CapacityCounterReceipt frozenReceipt(CommandContext context) {
        return jdbc.sql("""
                        SELECT capacity_counter_id, responsibility_level,
                               assignee_id, business_type, max_units, occupied_units,
                               counter_version AS version, occurred_at
                          FROM dsp_capacity_command_result
                         WHERE tenant_id = :tenantId AND operation_type = :operation
                           AND idempotency_key = :idempotencyKey
                        """)
                .param("tenantId", context.tenantId()).param("operation", OPERATION)
                .param("idempotencyKey", context.idempotencyKey())
                .query(CapacityCounterReceipt.class).single();
    }

    private void appendEvent(CommandContext context, CapacityCounterReceipt receipt) {
        CapacityConfiguredPayload payload = new CapacityConfiguredPayload(
                receipt.capacityCounterId(), receipt.responsibilityLevel().name(),
                receipt.assigneeId(), receipt.businessType(), receipt.maxUnits(),
                receipt.occupiedUnits(), receipt.version(), receipt.occurredAt());
        String json = serialize(payload);
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "dispatch", "dispatch.capacity-configured", 1,
                "CapacityCounter", receipt.capacityCounterId().toString(), receipt.version(),
                context.tenantId(), context.correlationId(), context.idempotencyKey(),
                receipt.capacityCounterId().toString(), json, Sha256.digest(json), receipt.occurredAt()));
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Capacity payload cannot be serialized", exception);
        }
    }

    private record CapacityState(UUID capacityCounterId, int occupiedUnits, long version) {
    }
}
