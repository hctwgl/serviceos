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
import com.serviceos.dispatch.api.ResponsibilityLevel;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.jooq.generated.tables.DspCapacityCommandResult;
import com.serviceos.jooq.generated.tables.DspCapacityCounter;
import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.jooq.Record8;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.DspCapacityCommandResult.DSP_CAPACITY_COMMAND_RESULT;
import static com.serviceos.jooq.generated.tables.DspCapacityCounter.DSP_CAPACITY_COUNTER;

/** 容量硬门禁的权威配置服务；占用量只能由 reservation 事务修改。 */
@Service
final class JooqCapacityAuthorityService implements CapacityAuthorityService {
    private static final String OPERATION = "dispatch.capacity.configure";
    private static final String CAPABILITY = "dispatch.capacity.configure";

    private final DSLContext dsl;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    JooqCapacityAuthorityService(
            DSLContext dsl,
            AuthorizationService authorization,
            IdempotencyService idempotency,
            AuditAppender audit,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.dsl = dsl;
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
        // M196：Network Portal 委托期间按 NETWORK scope 鉴权；Admin TENANT 路径不变。
        String resourceId = command.responsibilityLevel() + ":" + command.assigneeId()
                + ":" + command.businessType();
        String networkScope = NetworkScopedDispatchAuthorization.currentNetworkId();
        AuthorizationDecision authorizationDecision = networkScope != null
                ? authorization.require(principal,
                AuthorizationRequest.networkCapability(
                        CAPABILITY, context.tenantId(), "CapacityCounter", resourceId, networkScope),
                context.correlationId())
                : authorization.require(principal,
                AuthorizationRequest.tenantCapability(
                        CAPABILITY, context.tenantId(), "CapacityCounter", resourceId),
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
        DspCapacityCounter counter = DSP_CAPACITY_COUNTER;
        try {
            dsl.insertInto(counter)
                    .set(counter.CAPACITY_COUNTER_ID, counterId)
                    .set(counter.TENANT_ID, context.tenantId())
                    .set(counter.RESPONSIBILITY_LEVEL, command.responsibilityLevel().name())
                    .set(counter.ASSIGNEE_ID, command.assigneeId())
                    .set(counter.BUSINESS_TYPE, command.businessType())
                    .set(counter.MAX_UNITS, command.maxUnits())
                    .set(counter.OCCUPIED_UNITS, 0)
                    .set(counter.VERSION, 1L)
                    .set(counter.UPDATED_BY, context.actorId())
                    .set(counter.UPDATED_AT, now)
                    .execute();
        } catch (DuplicateKeyException exception) {
            // 唯一键 (tenant, level, assignee, businessType) 冲突即“计数器已存在”，
            // 与原实现一致地翻译为版本冲突语义。jOOQ 异常由 Boot 注册的
            // ExceptionTranslatorExecuteListener 转译为 Spring DuplicateKeyException。
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
        DspCapacityCounter counter = DSP_CAPACITY_COUNTER;
        // 乐观并发：版本条件 + 不允许把上限调到当前占用之下，影响行数不为 1 即失败关闭。
        int updated = dsl.update(counter)
                .set(counter.MAX_UNITS, command.maxUnits())
                .set(counter.VERSION, counter.VERSION.plus(1))
                .set(counter.UPDATED_BY, context.actorId())
                .set(counter.UPDATED_AT, now)
                .where(counter.TENANT_ID.eq(context.tenantId()))
                .and(counter.RESPONSIBILITY_LEVEL.eq(command.responsibilityLevel().name()))
                .and(counter.ASSIGNEE_ID.eq(command.assigneeId()))
                .and(counter.BUSINESS_TYPE.eq(command.businessType()))
                .and(counter.VERSION.eq(command.expectedVersion()))
                .and(counter.OCCUPIED_UNITS.le(command.maxUnits()))
                .execute();
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
        DspCapacityCounter counter = DSP_CAPACITY_COUNTER;
        return dsl.select(counter.CAPACITY_COUNTER_ID, counter.OCCUPIED_UNITS, counter.VERSION)
                .from(counter)
                .where(counter.TENANT_ID.eq(tenantId))
                .and(counter.RESPONSIBILITY_LEVEL.eq(command.responsibilityLevel().name()))
                .and(counter.ASSIGNEE_ID.eq(command.assigneeId()))
                .and(counter.BUSINESS_TYPE.eq(command.businessType()))
                .fetchOptional(JooqCapacityAuthorityService::mapState)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Capacity counter does not exist"));
    }

    private void insertFrozenReceipt(CommandContext context, CapacityCounterReceipt receipt) {
        DspCapacityCommandResult result = DSP_CAPACITY_COMMAND_RESULT;
        dsl.insertInto(result)
                .set(result.TENANT_ID, context.tenantId())
                .set(result.OPERATION_TYPE, OPERATION)
                .set(result.IDEMPOTENCY_KEY, context.idempotencyKey())
                .set(result.CAPACITY_COUNTER_ID, receipt.capacityCounterId())
                .set(result.RESPONSIBILITY_LEVEL, receipt.responsibilityLevel().name())
                .set(result.ASSIGNEE_ID, receipt.assigneeId())
                .set(result.BUSINESS_TYPE, receipt.businessType())
                .set(result.MAX_UNITS, receipt.maxUnits())
                .set(result.OCCUPIED_UNITS, receipt.occupiedUnits())
                .set(result.COUNTER_VERSION, receipt.version())
                .set(result.OCCURRED_AT, receipt.occurredAt())
                .execute();
    }

    private CapacityCounterReceipt frozenReceipt(CommandContext context) {
        DspCapacityCommandResult result = DSP_CAPACITY_COMMAND_RESULT;
        return dsl.select(
                        result.CAPACITY_COUNTER_ID,
                        result.RESPONSIBILITY_LEVEL,
                        result.ASSIGNEE_ID,
                        result.BUSINESS_TYPE,
                        result.MAX_UNITS,
                        result.OCCUPIED_UNITS,
                        result.COUNTER_VERSION,
                        result.OCCURRED_AT)
                .from(result)
                .where(result.TENANT_ID.eq(context.tenantId()))
                .and(result.OPERATION_TYPE.eq(OPERATION))
                .and(result.IDEMPOTENCY_KEY.eq(context.idempotencyKey()))
                .fetchSingle(JooqCapacityAuthorityService::mapReceipt);
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

    private static CapacityState mapState(Record3<UUID, Integer, Long> row) {
        return new CapacityState(row.value1(), row.value2(), row.value3());
    }

    private static CapacityCounterReceipt mapReceipt(Record8<
            UUID, String, String, String, Integer, Integer, Long, Instant> row) {
        return new CapacityCounterReceipt(
                row.value1(), ResponsibilityLevel.valueOf(row.value2()), row.value3(), row.value4(),
                row.value5(), row.value6(), row.value7(), row.value8());
    }

    private record CapacityState(UUID capacityCounterId, int occupiedUnits, long version) {
    }
}
