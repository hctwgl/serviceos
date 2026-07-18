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
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.infrastructure.PostgresJdbcParameters;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Bundle 通道激活服务（含多槽位 CANARY 与满量自动晋级）。
 *
 * <p>事务边界：supersede/插入/流量调整/晋级同事务。发布 Bundle 内容永不修改。</p>
 */
@Service
final class DefaultBundleChannelActivationService implements BundleChannelActivationService {
    private static final String MANAGE = "configuration.release.manage";
    private static final String RESOURCE = "BundleChannelActivation";

    private final JdbcClient jdbc;
    private final AuthorizationService authorization;
    private final Clock clock;

    DefaultBundleChannelActivationService(
            JdbcClient jdbc,
            AuthorizationService authorization,
            Clock clock
    ) {
        this.jdbc = jdbc;
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
        int updated = jdbc.sql("""
                        UPDATE cfg_bundle_channel_activation
                           SET traffic_percent = :trafficPercent,
                               aggregate_version = aggregate_version + 1
                         WHERE tenant_id = :tenantId
                           AND activation_id = :activationId
                           AND aggregate_version = :expectedVersion
                           AND status = 'ACTIVE'
                           AND channel = 'CANARY'
                        """)
                .param("trafficPercent", command.trafficPercent())
                .param("tenantId", principal.tenantId())
                .param("activationId", command.activationId())
                .param("expectedVersion", command.expectedVersion())
                .update();
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
        int updated = jdbc.sql("""
                        UPDATE cfg_bundle_channel_activation
                           SET status = 'SUPERSEDED',
                               superseded_at = :now,
                               approval_ref = :approvalRef,
                               aggregate_version = aggregate_version + 1
                         WHERE tenant_id = :tenantId
                           AND activation_id = :activationId
                           AND status = 'ACTIVE'
                           AND aggregate_version = :expectedVersion
                        """)
                .param("now", PostgresJdbcParameters.timestamptz(now))
                .param("approvalRef", normalizedApproval)
                .param("tenantId", principal.tenantId())
                .param("activationId", command.activationId())
                .param("expectedVersion", command.expectedVersion())
                .update();
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
        return jdbc.sql("""
                        SELECT a.*, b.bundle_code, b.bundle_version
                          FROM cfg_bundle_channel_activation a
                          JOIN cfg_configuration_bundle b
                            ON b.tenant_id = a.tenant_id AND b.bundle_id = a.bundle_id
                         WHERE a.tenant_id = :tenantId AND a.project_id = :projectId
                         ORDER BY a.activated_at DESC
                         LIMIT 100
                        """)
                .param("tenantId", principal.tenantId())
                .param("projectId", projectId)
                .query((rs, rowNum) -> map(rs))
                .list();
    }

    private void requirePublishedBundle(String tenantId, UUID projectId, UUID bundleId) {
        Integer count = jdbc.sql("""
                        SELECT COUNT(1) FROM cfg_configuration_bundle
                         WHERE tenant_id = :tenantId
                           AND project_id = :projectId
                           AND bundle_id = :bundleId
                           AND status = 'PUBLISHED'
                        """)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("bundleId", bundleId)
                .query(Integer.class)
                .single();
        if (count == null || count != 1) {
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
        Integer others = jdbc.sql("""
                        SELECT COALESCE(SUM(traffic_percent), 0)
                          FROM cfg_bundle_channel_activation
                         WHERE tenant_id = :tenantId
                           AND project_id = :projectId
                           AND channel = 'CANARY'
                           AND status = 'ACTIVE'
                           AND slot_code <> :slotCode
                        """)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("slotCode", slotCode)
                .query(Integer.class)
                .single();
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
        return jdbc.sql("""
                        SELECT activation_id FROM cfg_bundle_channel_activation
                         WHERE tenant_id = :tenantId
                           AND project_id = :projectId
                           AND channel = :channel
                           AND slot_code = :slotCode
                           AND status = 'ACTIVE'
                        """)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("channel", channel.name())
                .param("slotCode", slotCode)
                .query(UUID.class)
                .optional()
                .orElse(null);
    }

    private void supersede(String tenantId, UUID activationId, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE cfg_bundle_channel_activation
                           SET status = 'SUPERSEDED',
                               superseded_at = :now,
                               aggregate_version = aggregate_version + 1
                         WHERE tenant_id = :tenantId
                           AND activation_id = :activationId
                           AND status = 'ACTIVE'
                        """)
                .param("now", PostgresJdbcParameters.timestamptz(now))
                .param("tenantId", tenantId)
                .param("activationId", activationId)
                .update();
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
        jdbc.sql("""
                        UPDATE cfg_bundle_channel_activation
                           SET status = 'SUPERSEDED',
                               superseded_at = :now,
                               aggregate_version = aggregate_version + 1
                         WHERE tenant_id = :tenantId
                           AND project_id = :projectId
                           AND channel = 'CANARY'
                           AND status = 'ACTIVE'
                           AND activation_id <> :keepId
                        """)
                .param("now", PostgresJdbcParameters.timestamptz(now))
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("keepId", keepActivationId)
                .update();
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
        jdbc.sql("""
                        INSERT INTO cfg_bundle_channel_activation (
                            activation_id, tenant_id, project_id, channel, slot_code, bundle_id,
                            previous_activation_id, status, approval_ref, traffic_percent,
                            activated_by, activated_at, superseded_at, aggregate_version
                        ) VALUES (
                            :activationId, :tenantId, :projectId, :channel, :slotCode, :bundleId,
                            :previousId, 'ACTIVE', :approvalRef, :trafficPercent,
                            :actor, :now, NULL, 1
                        )
                        """)
                .param("activationId", activationId)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("channel", channel.name())
                .param("slotCode", slotCode)
                .param("bundleId", bundleId)
                .param("previousId", previousActivationId)
                .param("approvalRef", approvalRef)
                .param("trafficPercent", trafficPercent)
                .param("actor", actor)
                .param("now", PostgresJdbcParameters.timestamptz(now))
                .update();
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
        return jdbc.sql("""
                        SELECT a.*, b.bundle_code, b.bundle_version
                          FROM cfg_bundle_channel_activation a
                          JOIN cfg_configuration_bundle b
                            ON b.tenant_id = a.tenant_id AND b.bundle_id = a.bundle_id
                         WHERE a.tenant_id = :tenantId AND a.activation_id = :activationId
                        """)
                .param("tenantId", tenantId)
                .param("activationId", activationId)
                .query((rs, rowNum) -> map(rs))
                .optional()
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "通道激活不存在"));
    }

    private BundleChannelActivationView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new BundleChannelActivationView(
                rs.getObject("activation_id", UUID.class),
                rs.getObject("project_id", UUID.class),
                BundleChannel.valueOf(rs.getString("channel")),
                rs.getString("slot_code"),
                rs.getObject("bundle_id", UUID.class),
                rs.getString("bundle_code"),
                rs.getString("bundle_version"),
                rs.getObject("previous_activation_id", UUID.class),
                rs.getString("status"),
                rs.getString("approval_ref"),
                rs.getInt("traffic_percent"),
                rs.getString("activated_by"),
                rs.getTimestamp("activated_at").toInstant(),
                rs.getTimestamp("superseded_at") == null
                        ? null : rs.getTimestamp("superseded_at").toInstant(),
                rs.getLong("aggregate_version"));
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
