package com.serviceos.configuration.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.ActivateBundleChannelCommand;
import com.serviceos.configuration.api.AdjustCanaryTrafficCommand;
import com.serviceos.configuration.api.BundleChannel;
import com.serviceos.configuration.api.BundleChannelActivationService;
import com.serviceos.configuration.api.BundleChannelActivationView;
import com.serviceos.configuration.api.DeactivateBundleChannelCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.jooq.generated.tables.CfgBundleChannelActivation;
import com.serviceos.jooq.generated.tables.CfgConfigurationBundle;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import org.jooq.impl.DSL;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record15;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.CfgBundleChannelActivation.CFG_BUNDLE_CHANNEL_ACTIVATION;
import static com.serviceos.jooq.generated.tables.CfgConfigurationBundle.CFG_CONFIGURATION_BUNDLE;

/**
 * Bundle 通道激活服务（含多槽位 CANARY 与满量自动晋级）（jOOQ）。
 *
 * <p>事务边界：supersede/插入/流量调整/晋级同事务。发布 Bundle 内容永不修改。</p>
 */
@Service
final class JooqBundleChannelActivationService implements BundleChannelActivationService {
    private static final String MANAGE = "configuration.release.manage";
    private static final String RESOURCE = "BundleChannelActivation";

    private final DSLContext dsl;
    private final AuthorizationService authorization;
    private final Clock clock;

    JooqBundleChannelActivationService(
            DSLContext dsl,
            AuthorizationService authorization,
            Clock clock
    ) {
        this.dsl = dsl;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Override
    @Transactional
    public BundleChannelActivationView activate(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ActivateBundleChannelCommand command
    ) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.projectId(), "projectId");
        Objects.requireNonNull(command.channel(), "channel");
        Objects.requireNonNull(command.bundleId(), "bundleId");
        String approvalRef = requireText(command.approvalRef(), "approvalRef", 128);
        String slotCode = normalizeSlot(command.channel(), command.slotCode());
        authorization.require(principal, AuthorizationRequest.projectCapability(
                MANAGE, principal.tenantId(), RESOURCE, command.bundleId().toString(),
                command.projectId().toString()), metadata.correlationId());

        requirePublishedBundle(principal.tenantId(), command.projectId(), command.bundleId());
        int trafficPercent = resolveTrafficPercent(command.channel(), command.trafficPercent());
        requireCanaryBudget(principal.tenantId(), command.projectId(), slotCode, trafficPercent);

        UUID previous = findActiveId(principal.tenantId(), command.projectId(), command.channel(), slotCode);
        Instant now = clock.instant();
        if (previous != null) {
            supersede(principal.tenantId(), previous, now);
        }
        UUID activationId = UUID.randomUUID();
        insertActive(activationId, principal.tenantId(), command.projectId(), command.channel(), slotCode,
                command.bundleId(), previous, approvalRef, trafficPercent, principal.principalId(), now);

        if (command.channel() == BundleChannel.CANARY
                && command.autoPromoteWhenFull()
                && trafficPercent == 100) {
            return promoteCanary(principal, metadata, activationId, approvalRef);
        }
        return requireView(principal.tenantId(), activationId);
    }

    @Override
    @Transactional
    public BundleChannelActivationView adjustCanaryTraffic(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            AdjustCanaryTrafficCommand command
    ) {
        Objects.requireNonNull(command, "command");
        BundleChannelActivationView current = requireView(principal.tenantId(), command.activationId());
        if (current.channel() != BundleChannel.CANARY || !"ACTIVE".equals(current.status())) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "仅 ACTIVE CANARY 可调整流量");
        }
        if (command.trafficPercent() < 0 || command.trafficPercent() > 100) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "CANARY trafficPercent 必须在 0～100");
        }
        authorization.require(principal, AuthorizationRequest.projectCapability(
                MANAGE, principal.tenantId(), RESOURCE, command.activationId().toString(),
                current.projectId().toString()), metadata.correlationId());
        boolean autoPromoteFull = command.autoPromoteWhenFull() && command.trafficPercent() == 100;
        if (!autoPromoteFull) {
            requireCanaryBudget(
                    principal.tenantId(), current.projectId(), current.slotCode(),
                    command.trafficPercent());
        }

        Instant now = clock.instant();
        CfgBundleChannelActivation a = CFG_BUNDLE_CHANNEL_ACTIVATION;
        int updated = dsl.update(a)
                .set(a.TRAFFIC_PERCENT, command.trafficPercent())
                .set(a.AGGREGATE_VERSION, a.AGGREGATE_VERSION.plus(1))
                .where(a.TENANT_ID.eq(principal.tenantId()))
                .and(a.ACTIVATION_ID.eq(command.activationId()))
                .and(a.AGGREGATE_VERSION.eq(command.expectedVersion()))
                .and(a.STATUS.eq("ACTIVE"))
                .and(a.CHANNEL.eq("CANARY"))
                .execute();
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "CANARY 流量调整版本冲突");
        }
        if (autoPromoteFull) {
            // 满量自动晋级：先停用其它 CANARY 槽位，避免与 STABLE 并存的歧义流量。
            supersedeOtherCanaries(principal.tenantId(), current.projectId(), command.activationId(), now);
            return promoteCanary(
                    principal, metadata, command.activationId(), "AUTO-PROMOTE-" + now.toEpochMilli());
        }
        return requireView(principal.tenantId(), command.activationId());
    }

    @Override
    @Transactional
    public BundleChannelActivationView promoteCanary(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            UUID canaryActivationId,
            String approvalRef
    ) {
        Objects.requireNonNull(canaryActivationId, "canaryActivationId");
        String normalizedApproval = requireText(approvalRef, "approvalRef", 128);
        BundleChannelActivationView canary = requireView(principal.tenantId(), canaryActivationId);
        if (canary.channel() != BundleChannel.CANARY || !"ACTIVE".equals(canary.status())) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "仅 ACTIVE CANARY 可晋级为 STABLE");
        }
        authorization.require(principal, AuthorizationRequest.projectCapability(
                MANAGE, principal.tenantId(), RESOURCE, canaryActivationId.toString(),
                canary.projectId().toString()), metadata.correlationId());

        Instant now = clock.instant();
        UUID previousStable = findActiveId(
                principal.tenantId(), canary.projectId(), BundleChannel.STABLE, "primary");
        if (previousStable != null) {
            supersede(principal.tenantId(), previousStable, now);
        }
        supersede(principal.tenantId(), canaryActivationId, now);
        UUID stableId = UUID.randomUUID();
        insertActive(stableId, principal.tenantId(), canary.projectId(), BundleChannel.STABLE, "primary",
                canary.bundleId(), previousStable, normalizedApproval, 100, principal.principalId(), now);
        return requireView(principal.tenantId(), stableId);
    }

    @Override
    @Transactional
    public BundleChannelActivationView rollbackStable(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            UUID stableActivationId,
            String approvalRef
    ) {
        Objects.requireNonNull(stableActivationId, "stableActivationId");
        String normalizedApproval = requireText(approvalRef, "approvalRef", 128);
        BundleChannelActivationView current = requireView(principal.tenantId(), stableActivationId);
        if (current.channel() != BundleChannel.STABLE || !"ACTIVE".equals(current.status())) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "仅 ACTIVE STABLE 可回滚");
        }
        if (current.previousActivationId() == null) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "当前 STABLE 没有可回滚的上一激活");
        }
        authorization.require(principal, AuthorizationRequest.projectCapability(
                MANAGE, principal.tenantId(), RESOURCE, stableActivationId.toString(),
                current.projectId().toString()), metadata.correlationId());

        BundleChannelActivationView previous = requireView(
                principal.tenantId(), current.previousActivationId());
        Instant now = clock.instant();
        supersede(principal.tenantId(), stableActivationId, now);
        UUID rollbackId = UUID.randomUUID();
        insertActive(rollbackId, principal.tenantId(), current.projectId(), BundleChannel.STABLE, "primary",
                previous.bundleId(), stableActivationId, normalizedApproval, 100,
                principal.principalId(), now);
        return requireView(principal.tenantId(), rollbackId);
    }

    @Override
    @Transactional
    public BundleChannelActivationView deactivate(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            DeactivateBundleChannelCommand command
    ) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.activationId(), "activationId");
        String normalizedApproval = requireText(command.approvalRef(), "approvalRef", 128);
        BundleChannelActivationView current = requireView(principal.tenantId(), command.activationId());
        if (!"ACTIVE".equals(current.status())) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "仅 ACTIVE 通道激活可停用");
        }
        if (current.aggregateVersion() != command.expectedVersion()) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "通道激活版本冲突，无法停用");
        }
        authorization.require(principal, AuthorizationRequest.projectCapability(
                MANAGE, principal.tenantId(), RESOURCE, command.activationId().toString(),
                current.projectId().toString()), metadata.correlationId());

        Instant now = clock.instant();
        CfgBundleChannelActivation a = CFG_BUNDLE_CHANNEL_ACTIVATION;
        int updated = dsl.update(a)
                .set(a.STATUS, "SUPERSEDED")
                .set(a.SUPERSEDED_AT, now)
                .set(a.APPROVAL_REF, normalizedApproval)
                .set(a.AGGREGATE_VERSION, a.AGGREGATE_VERSION.plus(1))
                .where(a.TENANT_ID.eq(principal.tenantId()))
                .and(a.ACTIVATION_ID.eq(command.activationId()))
                .and(a.STATUS.eq("ACTIVE"))
                .and(a.AGGREGATE_VERSION.eq(command.expectedVersion()))
                .execute();
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "通道激活已变更，无法停用");
        }
        return requireView(principal.tenantId(), command.activationId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BundleChannelActivationView> list(
            CurrentPrincipal principal,
            String correlationId,
            UUID projectId
    ) {
        Objects.requireNonNull(projectId, "projectId");
        authorization.require(principal, AuthorizationRequest.projectCapability(
                MANAGE, principal.tenantId(), RESOURCE, projectId.toString(),
                projectId.toString()), correlationId);
        CfgBundleChannelActivation a = CFG_BUNDLE_CHANNEL_ACTIVATION;
        CfgConfigurationBundle b = CFG_CONFIGURATION_BUNDLE;
        return viewQuery(a, b)
                .where(a.TENANT_ID.eq(principal.tenantId()))
                .and(a.PROJECT_ID.eq(projectId))
                .orderBy(a.ACTIVATED_AT.desc())
                .limit(100)
                .fetch(this::map);
    }

    private void requirePublishedBundle(String tenantId, UUID projectId, UUID bundleId) {
        CfgConfigurationBundle b = CFG_CONFIGURATION_BUNDLE;
        int count = dsl.fetchCount(b,
                b.TENANT_ID.eq(tenantId),
                b.PROJECT_ID.eq(projectId),
                b.BUNDLE_ID.eq(bundleId),
                b.STATUS.eq("PUBLISHED"));
        if (count != 1) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "通道激活必须引用同项目已发布 Bundle");
        }
    }

    private void requireCanaryBudget(
            String tenantId,
            UUID projectId,
            String slotCode,
            int trafficPercent
    ) {
        CfgBundleChannelActivation a = CFG_BUNDLE_CHANNEL_ACTIVATION;
        // 其它槽位流量合计；无 ACTIVE CANARY 时 SUM 为 NULL，按 0 处理（与旧 COALESCE 一致）。
        Integer others = dsl.select(DSL.coalesce(DSL.sum(a.TRAFFIC_PERCENT), 0))
                .from(a)
                .where(a.TENANT_ID.eq(tenantId))
                .and(a.PROJECT_ID.eq(projectId))
                .and(a.CHANNEL.eq("CANARY"))
                .and(a.STATUS.eq("ACTIVE"))
                .and(a.SLOT_CODE.ne(slotCode))
                .fetchSingleInto(Integer.class);
        int total = (others == null ? 0 : others) + trafficPercent;
        if (total > 100) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "ACTIVE CANARY 槽位流量合计不得超过 100，当前将达到 " + total);
        }
    }

    private UUID findActiveId(
            String tenantId,
            UUID projectId,
            BundleChannel channel,
            String slotCode
    ) {
        CfgBundleChannelActivation a = CFG_BUNDLE_CHANNEL_ACTIVATION;
        return dsl.select(a.ACTIVATION_ID)
                .from(a)
                .where(a.TENANT_ID.eq(tenantId))
                .and(a.PROJECT_ID.eq(projectId))
                .and(a.CHANNEL.eq(channel.name()))
                .and(a.SLOT_CODE.eq(slotCode))
                .and(a.STATUS.eq("ACTIVE"))
                .fetchOptional(a.ACTIVATION_ID)
                .orElse(null);
    }

    private void supersede(String tenantId, UUID activationId, Instant now) {
        CfgBundleChannelActivation a = CFG_BUNDLE_CHANNEL_ACTIVATION;
        // 带状态条件的迁移：只允许 ACTIVE -> SUPERSEDED，影响行数不为 1 说明前置状态被破坏。
        int updated = dsl.update(a)
                .set(a.STATUS, "SUPERSEDED")
                .set(a.SUPERSEDED_AT, now)
                .set(a.AGGREGATE_VERSION, a.AGGREGATE_VERSION.plus(1))
                .where(a.TENANT_ID.eq(tenantId))
                .and(a.ACTIVATION_ID.eq(activationId))
                .and(a.STATUS.eq("ACTIVE"))
                .execute();
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "通道激活已变更，无法 supersede");
        }
    }

    private void supersedeOtherCanaries(
            String tenantId,
            UUID projectId,
            UUID keepActivationId,
            Instant now
    ) {
        CfgBundleChannelActivation a = CFG_BUNDLE_CHANNEL_ACTIVATION;
        dsl.update(a)
                .set(a.STATUS, "SUPERSEDED")
                .set(a.SUPERSEDED_AT, now)
                .set(a.AGGREGATE_VERSION, a.AGGREGATE_VERSION.plus(1))
                .where(a.TENANT_ID.eq(tenantId))
                .and(a.PROJECT_ID.eq(projectId))
                .and(a.CHANNEL.eq("CANARY"))
                .and(a.STATUS.eq("ACTIVE"))
                .and(a.ACTIVATION_ID.ne(keepActivationId))
                .execute();
    }

    private void insertActive(
            UUID activationId,
            String tenantId,
            UUID projectId,
            BundleChannel channel,
            String slotCode,
            UUID bundleId,
            UUID previousActivationId,
            String approvalRef,
            int trafficPercent,
            String actor,
            Instant now
    ) {
        CfgBundleChannelActivation a = CFG_BUNDLE_CHANNEL_ACTIVATION;
        dsl.insertInto(a)
                .set(a.ACTIVATION_ID, activationId)
                .set(a.TENANT_ID, tenantId)
                .set(a.PROJECT_ID, projectId)
                .set(a.CHANNEL, channel.name())
                .set(a.SLOT_CODE, slotCode)
                .set(a.BUNDLE_ID, bundleId)
                .set(a.PREVIOUS_ACTIVATION_ID, previousActivationId)
                .set(a.STATUS, "ACTIVE")
                .set(a.APPROVAL_REF, approvalRef)
                .set(a.TRAFFIC_PERCENT, trafficPercent)
                .set(a.ACTIVATED_BY, actor)
                .set(a.ACTIVATED_AT, now)
                .set(a.AGGREGATE_VERSION, 1L)
                .execute();
    }

    private static int resolveTrafficPercent(BundleChannel channel, Integer trafficPercent) {
        if (channel == BundleChannel.STABLE) {
            if (trafficPercent != null && trafficPercent != 100) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "STABLE 通道 trafficPercent 必须为 100");
            }
            return 100;
        }
        int value = trafficPercent == null ? 0 : trafficPercent;
        if (value < 0 || value > 100) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "CANARY trafficPercent 必须在 0～100");
        }
        return value;
    }

    private static String normalizeSlot(BundleChannel channel, String slotCode) {
        if (channel == BundleChannel.STABLE) {
            return "primary";
        }
        String value = slotCode == null || slotCode.isBlank() ? "primary" : slotCode.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("^[a-z][a-z0-9_-]{0,63}$")) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "slotCode 格式非法");
        }
        return value;
    }

    private BundleChannelActivationView requireView(String tenantId, UUID activationId) {
        CfgBundleChannelActivation a = CFG_BUNDLE_CHANNEL_ACTIVATION;
        CfgConfigurationBundle b = CFG_CONFIGURATION_BUNDLE;
        return viewQuery(a, b)
                .where(a.TENANT_ID.eq(tenantId))
                .and(a.ACTIVATION_ID.eq(activationId))
                .fetchOptional(this::map)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "通道激活不存在"));
    }

    private org.jooq.SelectJoinStep<Record15<
            UUID, UUID, String, String, UUID, String, String, UUID, String, String,
            Integer, String, Instant, Instant, Long>> viewQuery(
            CfgBundleChannelActivation a,
            CfgConfigurationBundle b
    ) {
        return dsl.select(a.ACTIVATION_ID, a.PROJECT_ID, a.CHANNEL, a.SLOT_CODE, a.BUNDLE_ID,
                        b.BUNDLE_CODE, b.BUNDLE_VERSION, a.PREVIOUS_ACTIVATION_ID, a.STATUS,
                        a.APPROVAL_REF, a.TRAFFIC_PERCENT, a.ACTIVATED_BY, a.ACTIVATED_AT,
                        a.SUPERSEDED_AT, a.AGGREGATE_VERSION)
                .from(a)
                .join(b)
                .on(b.TENANT_ID.eq(a.TENANT_ID))
                .and(b.BUNDLE_ID.eq(a.BUNDLE_ID));
    }

    private BundleChannelActivationView map(Record record) {
        CfgBundleChannelActivation a = CFG_BUNDLE_CHANNEL_ACTIVATION;
        CfgConfigurationBundle b = CFG_CONFIGURATION_BUNDLE;
        return new BundleChannelActivationView(
                record.get(a.ACTIVATION_ID),
                record.get(a.PROJECT_ID),
                BundleChannel.valueOf(record.get(a.CHANNEL)),
                record.get(a.SLOT_CODE),
                record.get(a.BUNDLE_ID),
                record.get(b.BUNDLE_CODE),
                record.get(b.BUNDLE_VERSION),
                record.get(a.PREVIOUS_ACTIVATION_ID),
                record.get(a.STATUS),
                record.get(a.APPROVAL_REF),
                record.get(a.TRAFFIC_PERCENT),
                record.get(a.ACTIVATED_BY),
                record.get(a.ACTIVATED_AT),
                record.get(a.SUPERSEDED_AT),
                record.get(a.AGGREGATE_VERSION));
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new IllegalArgumentException(field + " exceeds max length");
        }
        return normalized;
    }
}
