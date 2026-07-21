package com.serviceos.authorization.infrastructure;

import com.serviceos.authorization.api.CapabilityView;
import com.serviceos.authorization.application.AuthorizationGovernanceRepository;
import com.serviceos.authorization.domain.AuthRole;
import com.serviceos.authorization.domain.DelegationRecord;
import com.serviceos.authorization.domain.RoleGrantRecord;
import com.serviceos.jooq.generated.tables.AuthCapability;
import com.serviceos.jooq.generated.tables.AuthDelegation;
import com.serviceos.jooq.generated.tables.AuthDelegationCapability;
import com.serviceos.jooq.generated.tables.AuthRoleCapability;
import com.serviceos.jooq.generated.tables.AuthRoleGrant;
import com.serviceos.jooq.generated.tables.AuthRoleGrantEvent;
import com.serviceos.jooq.generated.tables.AuthTenantGrantGeneration;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectWhereStep;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.AuthCapability.AUTH_CAPABILITY;
import static com.serviceos.jooq.generated.tables.AuthDelegation.AUTH_DELEGATION;
import static com.serviceos.jooq.generated.tables.AuthDelegationCapability.AUTH_DELEGATION_CAPABILITY;
import static com.serviceos.jooq.generated.tables.AuthRole.AUTH_ROLE;
import static com.serviceos.jooq.generated.tables.AuthRoleCapability.AUTH_ROLE_CAPABILITY;
import static com.serviceos.jooq.generated.tables.AuthRoleGrant.AUTH_ROLE_GRANT;
import static com.serviceos.jooq.generated.tables.AuthRoleGrantEvent.AUTH_ROLE_GRANT_EVENT;
import static com.serviceos.jooq.generated.tables.AuthTenantGrantGeneration.AUTH_TENANT_GRANT_GENERATION;
import static org.jooq.impl.DSL.excluded;

/**
 * 授权治理 jOOQ 适配器。可授予范围与委托子集校验在 SQL 中失败关闭，禁止只信应用层集合差。
 */
@Repository
final class JooqAuthorizationGovernanceRepository implements AuthorizationGovernanceRepository {
    private final DSLContext dsl;

    JooqAuthorizationGovernanceRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<CapabilityView> listCapabilities() {
        AuthCapability c = AUTH_CAPABILITY;
        return dsl.select(c.CAPABILITY_CODE, c.CAPABILITY_NAME, c.RISK_LEVEL)
                .from(c)
                .orderBy(c.CAPABILITY_CODE)
                .fetch(row -> new CapabilityView(row.value1(), row.value2(), row.value3()));
    }

    @Override
    public Optional<CapabilityView> findCapability(String capabilityCode) {
        AuthCapability c = AUTH_CAPABILITY;
        return dsl.select(c.CAPABILITY_CODE, c.CAPABILITY_NAME, c.RISK_LEVEL)
                .from(c)
                .where(c.CAPABILITY_CODE.eq(capabilityCode))
                .fetchOptional(row -> new CapabilityView(row.value1(), row.value2(), row.value3()));
    }

    @Override
    public Set<String> findExistingCapabilityCodes(List<String> capabilityCodes) {
        if (capabilityCodes.isEmpty()) {
            return Set.of();
        }
        AuthCapability c = AUTH_CAPABILITY;
        List<String> found = dsl.select(c.CAPABILITY_CODE)
                .from(c)
                .where(c.CAPABILITY_CODE.in(capabilityCodes))
                .fetch(c.CAPABILITY_CODE);
        return new HashSet<>(found);
    }

    @Override
    public List<String> findRoleCapabilityCodes(UUID roleId) {
        AuthRoleCapability rc = AUTH_ROLE_CAPABILITY;
        return dsl.select(rc.CAPABILITY_CODE)
                .from(rc)
                .where(rc.ROLE_ID.eq(roleId))
                .orderBy(rc.CAPABILITY_CODE)
                .fetch(rc.CAPABILITY_CODE);
    }

    @Override
    public List<AuthRole> listRoles(String tenantId) {
        List<AuthRole> roles = roleSelect()
                .where(AUTH_ROLE.TENANT_ID.eq(tenantId))
                .orderBy(AUTH_ROLE.ROLE_CODE)
                .fetch(this::mapRoleWithoutCapabilities);
        return roles.stream().map(this::withCapabilities).toList();
    }

    @Override
    public Optional<AuthRole> findRole(String tenantId, UUID roleId) {
        return roleQuery(tenantId, roleId, false);
    }

    @Override
    public Optional<AuthRole> findRoleForUpdate(String tenantId, UUID roleId) {
        return roleQuery(tenantId, roleId, true);
    }

    private Optional<AuthRole> roleQuery(String tenantId, UUID roleId, boolean forUpdate) {
        var query = roleSelect()
                .where(AUTH_ROLE.TENANT_ID.eq(tenantId))
                .and(AUTH_ROLE.ROLE_ID.eq(roleId));
        if (forUpdate) {
            // 与调用方事务配合串行化角色聚合变更；FOR UPDATE 锁定角色行。
            return query.forUpdate().fetchOptional(this::mapRoleWithoutCapabilities)
                    .map(this::withCapabilities);
        }
        return query.fetchOptional(this::mapRoleWithoutCapabilities)
                .map(this::withCapabilities);
    }

    private SelectWhereStep<? extends Record> roleSelect() {
        com.serviceos.jooq.generated.tables.AuthRole r = AUTH_ROLE;
        return dsl.select(
                        r.ROLE_ID, r.TENANT_ID, r.ROLE_CODE, r.ROLE_NAME, r.ROLE_KIND, r.ROLE_STATUS,
                        r.DESCRIPTION, r.AGGREGATE_VERSION, r.CREATED_AT, r.UPDATED_AT)
                .from(r);
    }

    @Override
    public void insertRole(AuthRole role) {
        com.serviceos.jooq.generated.tables.AuthRole r = AUTH_ROLE;
        dsl.insertInto(r)
                .set(r.ROLE_ID, role.id())
                .set(r.TENANT_ID, role.tenantId())
                .set(r.ROLE_CODE, role.roleCode())
                .set(r.ROLE_NAME, role.roleName())
                .set(r.ROLE_STATUS, role.roleStatus())
                .set(r.ROLE_KIND, role.roleKind().name())
                .set(r.DESCRIPTION, role.description())
                .set(r.AGGREGATE_VERSION, role.version())
                .set(r.CREATED_AT, role.createdAt())
                .set(r.UPDATED_AT, role.updatedAt())
                .execute();
    }

    @Override
    public void insertRoleCapabilities(UUID roleId, List<String> capabilityCodes, Instant grantedAt) {
        AuthRoleCapability rc = AUTH_ROLE_CAPABILITY;
        for (String code : capabilityCodes) {
            dsl.insertInto(rc)
                    .set(rc.ROLE_ID, roleId)
                    .set(rc.CAPABILITY_CODE, code)
                    .set(rc.GRANTED_AT, grantedAt)
                    .execute();
        }
    }

    @Override
    public List<RoleGrantRecord> listRoleGrants(String tenantId, String principalId, String grantStatus) {
        AuthRoleGrant g = AUTH_ROLE_GRANT.as("g");
        com.serviceos.jooq.generated.tables.AuthRole r = AUTH_ROLE.as("r");
        Condition condition = g.TENANT_ID.eq(tenantId);
        if (principalId != null) {
            condition = condition.and(g.PRINCIPAL_ID.eq(principalId));
        }
        if (grantStatus != null) {
            condition = condition.and(g.GRANT_STATUS.eq(grantStatus));
        }
        return grantSelect(g, r)
                .where(condition)
                .orderBy(g.CREATED_AT.desc(), g.GRANT_ID)
                .fetch(this::mapGrant);
    }

    @Override
    public Optional<RoleGrantRecord> findRoleGrant(String tenantId, UUID grantId) {
        return grantQuery(tenantId, grantId, false);
    }

    @Override
    public Optional<RoleGrantRecord> findRoleGrantForUpdate(String tenantId, UUID grantId) {
        return grantQuery(tenantId, grantId, true);
    }

    private Optional<RoleGrantRecord> grantQuery(String tenantId, UUID grantId, boolean forUpdate) {
        AuthRoleGrant g = AUTH_ROLE_GRANT.as("g");
        com.serviceos.jooq.generated.tables.AuthRole r = AUTH_ROLE.as("r");
        var query = grantSelect(g, r)
                .where(g.TENANT_ID.eq(tenantId))
                .and(g.GRANT_ID.eq(grantId));
        if (forUpdate) {
            // FOR UPDATE 未限定 OF：与原 SQL 一致，同时锁定 g 与 r 命中行。
            return query.forUpdate().fetchOptional(this::mapGrant);
        }
        return query.fetchOptional(this::mapGrant);
    }

    /** GRANT_SELECT：与 auth_role 联表取出 role_code。 */
    private SelectWhereStep<? extends Record> grantSelect(
            AuthRoleGrant g, com.serviceos.jooq.generated.tables.AuthRole r
    ) {
        return dsl.select(
                        g.GRANT_ID, g.TENANT_ID, g.PRINCIPAL_ID, g.ROLE_ID, r.ROLE_CODE,
                        g.SCOPE_TYPE, g.SCOPE_REF, g.GRANT_STATUS, g.GRANT_EFFECT,
                        g.VALID_FROM, g.VALID_TO, g.SOURCE_CODE, g.APPROVAL_REF,
                        g.REQUESTED_BY, g.REQUEST_REASON, g.APPROVED_BY, g.APPROVED_AT,
                        g.REJECTED_BY, g.REJECTED_AT, g.REJECT_REASON,
                        g.REVOKED_AT, g.REVOKED_BY, g.REVOKE_REASON,
                        g.AGGREGATE_VERSION, g.CREATED_AT, g.UPDATED_AT)
                .from(g)
                .join(r).on(r.ROLE_ID.eq(g.ROLE_ID));
    }

    @Override
    public void insertRoleGrant(RoleGrantRecord grant) {
        AuthRoleGrant g = AUTH_ROLE_GRANT;
        dsl.insertInto(g)
                .set(g.GRANT_ID, grant.id())
                .set(g.TENANT_ID, grant.tenantId())
                .set(g.PRINCIPAL_ID, grant.principalId())
                .set(g.ROLE_ID, grant.roleId())
                .set(g.SCOPE_TYPE, grant.scopeType())
                .set(g.SCOPE_REF, grant.scopeRef())
                .set(g.VALID_FROM, grant.validFrom())
                .set(g.VALID_TO, grant.validTo())
                .set(g.SOURCE_CODE, grant.sourceCode())
                .set(g.APPROVAL_REF, grant.approvalRef())
                .set(g.GRANT_STATUS, grant.grantStatus().name())
                .set(g.GRANT_EFFECT, grant.grantEffect().name())
                .set(g.REQUESTED_BY, grant.requestedBy())
                .set(g.REQUEST_REASON, grant.requestReason())
                .set(g.APPROVED_BY, grant.approvedBy())
                .set(g.APPROVED_AT, grant.approvedAt())
                .set(g.REJECTED_BY, grant.rejectedBy())
                .set(g.REJECTED_AT, grant.rejectedAt())
                .set(g.REJECT_REASON, grant.rejectReason())
                .set(g.REVOKED_AT, grant.revokedAt())
                .set(g.REVOKED_BY, grant.revokedBy())
                .set(g.REVOKE_REASON, grant.revokeReason())
                .set(g.AGGREGATE_VERSION, grant.version())
                .set(g.CREATED_AT, grant.createdAt())
                .set(g.UPDATED_AT, grant.updatedAt())
                .execute();
    }

    @Override
    public boolean updateRoleGrant(RoleGrantRecord grant, long expectedVersion) {
        AuthRoleGrant g = AUTH_ROLE_GRANT;
        // 乐观并发控制：仅当租户内该授权仍处于期望版本时更新，影响行数必须为 1。
        int updated = dsl.update(g)
                .set(g.GRANT_STATUS, grant.grantStatus().name())
                .set(g.APPROVED_BY, grant.approvedBy())
                .set(g.APPROVED_AT, grant.approvedAt())
                .set(g.REJECTED_BY, grant.rejectedBy())
                .set(g.REJECTED_AT, grant.rejectedAt())
                .set(g.REJECT_REASON, grant.rejectReason())
                .set(g.APPROVAL_REF, grant.approvalRef())
                .set(g.REVOKED_AT, grant.revokedAt())
                .set(g.REVOKED_BY, grant.revokedBy())
                .set(g.REVOKE_REASON, grant.revokeReason())
                .set(g.AGGREGATE_VERSION, grant.version())
                .set(g.UPDATED_AT, grant.updatedAt())
                .where(g.TENANT_ID.eq(grant.tenantId()))
                .and(g.GRANT_ID.eq(grant.id()))
                .and(g.AGGREGATE_VERSION.eq(expectedVersion))
                .execute();
        return updated == 1;
    }

    @Override
    public List<DelegationRecord> listDelegations(String tenantId, String delegatePrincipalId) {
        AuthDelegation d = AUTH_DELEGATION.as("d");
        Condition condition = d.TENANT_ID.eq(tenantId);
        if (delegatePrincipalId != null) {
            condition = condition.and(d.DELEGATE_PRINCIPAL_ID.eq(delegatePrincipalId));
        }
        return delegationSelect(d)
                .where(condition)
                .orderBy(d.CREATED_AT.desc(), d.DELEGATION_ID)
                .fetch(this::mapDelegationWithoutCapabilities)
                .stream()
                .map(this::withDelegationCapabilities)
                .toList();
    }

    @Override
    public Optional<DelegationRecord> findDelegation(String tenantId, UUID delegationId) {
        return delegationQuery(tenantId, delegationId, false);
    }

    @Override
    public Optional<DelegationRecord> findDelegationForUpdate(String tenantId, UUID delegationId) {
        return delegationQuery(tenantId, delegationId, true);
    }

    private Optional<DelegationRecord> delegationQuery(String tenantId, UUID delegationId, boolean forUpdate) {
        AuthDelegation d = AUTH_DELEGATION.as("d");
        var query = delegationSelect(d)
                .where(d.TENANT_ID.eq(tenantId))
                .and(d.DELEGATION_ID.eq(delegationId));
        if (forUpdate) {
            return query.forUpdate().fetchOptional(this::mapDelegationWithoutCapabilities)
                    .map(this::withDelegationCapabilities);
        }
        return query.fetchOptional(this::mapDelegationWithoutCapabilities)
                .map(this::withDelegationCapabilities);
    }

    private SelectWhereStep<? extends Record> delegationSelect(AuthDelegation d) {
        return dsl.select(
                        d.DELEGATION_ID, d.TENANT_ID, d.DELEGATOR_PRINCIPAL_ID, d.DELEGATE_PRINCIPAL_ID,
                        d.SCOPE_TYPE, d.SCOPE_REF, d.VALID_FROM, d.VALID_TO, d.REASON,
                        d.DELEGATION_STATUS, d.AGGREGATE_VERSION, d.CREATED_AT, d.UPDATED_AT,
                        d.REVOKED_AT, d.REVOKED_BY, d.REVOKE_REASON)
                .from(d);
    }

    @Override
    public void insertDelegation(DelegationRecord delegation) {
        AuthDelegation d = AUTH_DELEGATION;
        dsl.insertInto(d)
                .set(d.DELEGATION_ID, delegation.id())
                .set(d.TENANT_ID, delegation.tenantId())
                .set(d.DELEGATOR_PRINCIPAL_ID, delegation.delegatorPrincipalId())
                .set(d.DELEGATE_PRINCIPAL_ID, delegation.delegatePrincipalId())
                .set(d.SCOPE_TYPE, delegation.scopeType())
                .set(d.SCOPE_REF, delegation.scopeRef())
                .set(d.VALID_FROM, delegation.validFrom())
                .set(d.VALID_TO, delegation.validTo())
                .set(d.REASON, delegation.reason())
                .set(d.DELEGATION_STATUS, delegation.status().name())
                .set(d.CREATED_BY, delegation.delegatorPrincipalId())
                .set(d.CREATED_AT, delegation.createdAt())
                .set(d.AGGREGATE_VERSION, delegation.version())
                .set(d.UPDATED_AT, delegation.updatedAt())
                .execute();
        AuthDelegationCapability dc = AUTH_DELEGATION_CAPABILITY;
        for (String code : delegation.capabilityCodes()) {
            dsl.insertInto(dc)
                    .set(dc.DELEGATION_ID, delegation.id())
                    .set(dc.CAPABILITY_CODE, code)
                    .execute();
        }
    }

    @Override
    public boolean updateDelegation(DelegationRecord delegation, long expectedVersion) {
        AuthDelegation d = AUTH_DELEGATION;
        // 乐观并发控制：仅当租户内该委托仍处于期望版本时更新，影响行数必须为 1。
        int updated = dsl.update(d)
                .set(d.DELEGATION_STATUS, delegation.status().name())
                .set(d.REVOKED_BY, delegation.revokedBy())
                .set(d.REVOKED_AT, delegation.revokedAt())
                .set(d.REVOKE_REASON, delegation.revokeReason())
                .set(d.AGGREGATE_VERSION, delegation.version())
                .set(d.UPDATED_AT, delegation.updatedAt())
                .where(d.TENANT_ID.eq(delegation.tenantId()))
                .and(d.DELEGATION_ID.eq(delegation.id()))
                .and(d.AGGREGATE_VERSION.eq(expectedVersion))
                .execute();
        return updated == 1;
    }

    @Override
    public boolean approverCoversCapabilities(
            String tenantId, String approverPrincipalId, List<String> capabilityCodes,
            String scopeType, String scopeRef, Instant evaluatedAt
    ) {
        for (String capability : capabilityCodes) {
            if (!hasCoveringAllow(tenantId, approverPrincipalId, capability, scopeType, scopeRef,
                    evaluatedAt, null, null)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean delegatorCoversDelegation(
            String tenantId, String delegatorPrincipalId, List<String> capabilityCodes,
            String scopeType, String scopeRef, Instant validFrom, Instant validTo, Instant evaluatedAt
    ) {
        for (String capability : capabilityCodes) {
            if (!hasCoveringAllow(tenantId, delegatorPrincipalId, capability, scopeType, scopeRef,
                    evaluatedAt, validFrom, validTo)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 覆盖规则：TENANT 范围覆盖同租户任意更窄范围；同类型同引用精确覆盖。
     * 委托期限必须落在委托人有效授权区间内（valid_from ≤ 委托起；委托人 valid_to 为空或 ≥ 委托止）。
     * 仅绑定非空期限条件，与原实现"不向 JDBC 绑定空 timestamptz"的约束保持一致。
     */
    private boolean hasCoveringAllow(
            String tenantId, String principalId, String capability,
            String scopeType, String scopeRef, Instant evaluatedAt,
            Instant requiredFrom, Instant requiredTo
    ) {
        AuthRoleGrant g = AUTH_ROLE_GRANT.as("g");
        com.serviceos.jooq.generated.tables.AuthRole r = AUTH_ROLE.as("r");
        AuthRoleCapability rc = AUTH_ROLE_CAPABILITY.as("rc");
        Condition grantCondition = g.TENANT_ID.eq(tenantId)
                .and(g.PRINCIPAL_ID.eq(principalId))
                .and(rc.CAPABILITY_CODE.eq(capability))
                .and(r.TENANT_ID.eq(tenantId))
                .and(r.ROLE_STATUS.eq("ACTIVE"))
                .and(g.GRANT_STATUS.eq("ACTIVE"))
                .and(g.GRANT_EFFECT.eq("ALLOW"))
                .and(g.REVOKED_AT.isNull())
                .and(g.VALID_FROM.le(evaluatedAt))
                .and(g.VALID_TO.isNull().or(g.VALID_TO.gt(evaluatedAt)))
                .and(g.SCOPE_TYPE.eq("TENANT").and(g.SCOPE_REF.eq(tenantId))
                        .or(g.SCOPE_TYPE.eq(scopeType).and(g.SCOPE_REF.eq(scopeRef))));
        if (requiredFrom != null) {
            grantCondition = grantCondition.and(g.VALID_FROM.le(requiredFrom));
        }
        if (requiredTo != null) {
            grantCondition = grantCondition.and(g.VALID_TO.isNull().or(g.VALID_TO.ge(requiredTo)));
        }
        Long grantHits = dsl.selectCount()
                .from(g)
                .join(r).on(r.ROLE_ID.eq(g.ROLE_ID))
                .join(rc).on(rc.ROLE_ID.eq(g.ROLE_ID))
                .where(grantCondition)
                .fetchSingle(0, Long.class);
        if (grantHits != null && grantHits > 0) {
            return true;
        }

        AuthDelegation d = AUTH_DELEGATION.as("d");
        AuthDelegationCapability dc = AUTH_DELEGATION_CAPABILITY.as("dc");
        Condition delegationCondition = d.TENANT_ID.eq(tenantId)
                .and(d.DELEGATE_PRINCIPAL_ID.eq(principalId))
                .and(dc.CAPABILITY_CODE.eq(capability))
                .and(d.DELEGATION_STATUS.eq("ACTIVE"))
                .and(d.REVOKED_AT.isNull())
                .and(d.VALID_FROM.le(evaluatedAt))
                .and(d.VALID_TO.isNull().or(d.VALID_TO.gt(evaluatedAt)))
                .and(d.SCOPE_TYPE.eq("TENANT").and(d.SCOPE_REF.eq(tenantId))
                        .or(d.SCOPE_TYPE.eq(scopeType).and(d.SCOPE_REF.eq(scopeRef))));
        if (requiredFrom != null) {
            delegationCondition = delegationCondition.and(d.VALID_FROM.le(requiredFrom));
        }
        if (requiredTo != null) {
            delegationCondition = delegationCondition.and(d.VALID_TO.isNull().or(d.VALID_TO.ge(requiredTo)));
        }
        Long delegationHits = dsl.selectCount()
                .from(d)
                .join(dc).on(dc.DELEGATION_ID.eq(d.DELEGATION_ID))
                .where(delegationCondition)
                .fetchSingle(0, Long.class);
        return delegationHits != null && delegationHits > 0;
    }

    @Override
    public boolean roleHasHighOrCriticalCapability(UUID roleId) {
        AuthRoleCapability rc = AUTH_ROLE_CAPABILITY;
        AuthCapability c = AUTH_CAPABILITY;
        Long count = dsl.selectCount()
                .from(rc)
                .join(c).on(c.CAPABILITY_CODE.eq(rc.CAPABILITY_CODE))
                .where(rc.ROLE_ID.eq(roleId))
                .and(c.RISK_LEVEL.in("HIGH", "CRITICAL"))
                .fetchSingle(0, Long.class);
        return count != null && count > 0;
    }

    @Override
    public void insertEvent(
            UUID eventId, String tenantId, String eventType, String resourceType, UUID resourceId,
            long resourceVersion, String reason, String actorId, String requestDigest,
            String correlationId, Instant occurredAt
    ) {
        AuthRoleGrantEvent e = AUTH_ROLE_GRANT_EVENT;
        dsl.insertInto(e)
                .set(e.EVENT_ID, eventId)
                .set(e.TENANT_ID, tenantId)
                .set(e.EVENT_TYPE, eventType)
                .set(e.RESOURCE_TYPE, resourceType)
                .set(e.RESOURCE_ID, resourceId)
                .set(e.RESOURCE_VERSION, resourceVersion)
                .set(e.REASON, reason)
                .set(e.ACTOR_ID, actorId)
                .set(e.REQUEST_DIGEST, requestDigest)
                .set(e.CORRELATION_ID, correlationId)
                .set(e.OCCURRED_AT, occurredAt)
                .execute();
    }

    @Override
    public long bumpGrantGeneration(String tenantId, Instant updatedAt) {
        AuthTenantGrantGeneration t = AUTH_TENANT_GRANT_GENERATION;
        // 租户 grant generation 单调递增：首次插入 1，冲突时自增并刷新 updated_at。
        dsl.insertInto(t)
                .set(t.TENANT_ID, tenantId)
                .set(t.GENERATION, 1L)
                .set(t.UPDATED_AT, updatedAt)
                .onConflict(t.TENANT_ID)
                .doUpdate()
                .set(t.GENERATION, t.GENERATION.plus(1))
                .set(t.UPDATED_AT, excluded(t.UPDATED_AT))
                .execute();
        return currentGrantGeneration(tenantId);
    }

    @Override
    public long currentGrantGeneration(String tenantId) {
        AuthTenantGrantGeneration t = AUTH_TENANT_GRANT_GENERATION;
        return dsl.select(t.GENERATION)
                .from(t)
                .where(t.TENANT_ID.eq(tenantId))
                .fetchOptional(t.GENERATION)
                .orElse(0L);
    }

    private AuthRole withCapabilities(AuthRole role) {
        return new AuthRole(role.id(), role.tenantId(), role.roleCode(), role.roleName(),
                role.roleKind(), role.roleStatus(), role.description(),
                findRoleCapabilityCodes(role.id()), role.version(), role.createdAt(), role.updatedAt());
    }

    private DelegationRecord withDelegationCapabilities(DelegationRecord delegation) {
        AuthDelegationCapability dc = AUTH_DELEGATION_CAPABILITY;
        List<String> codes = dsl.select(dc.CAPABILITY_CODE)
                .from(dc)
                .where(dc.DELEGATION_ID.eq(delegation.id()))
                .orderBy(dc.CAPABILITY_CODE)
                .fetch(dc.CAPABILITY_CODE);
        return new DelegationRecord(delegation.id(), delegation.tenantId(),
                delegation.delegatorPrincipalId(), delegation.delegatePrincipalId(), codes,
                delegation.scopeType(), delegation.scopeRef(), delegation.validFrom(),
                delegation.validTo(), delegation.reason(), delegation.status(),
                delegation.version(), delegation.createdAt(), delegation.updatedAt(),
                delegation.revokedAt(), delegation.revokedBy(), delegation.revokeReason());
    }

    private AuthRole mapRoleWithoutCapabilities(Record row) {
        return new AuthRole(
                row.get("role_id", UUID.class),
                row.get("tenant_id", String.class),
                row.get("role_code", String.class),
                row.get("role_name", String.class),
                AuthRole.RoleKind.valueOf(row.get("role_kind", String.class)),
                row.get("role_status", String.class),
                row.get("description", String.class),
                List.of(),
                row.get("aggregate_version", Long.class),
                row.get("created_at", Instant.class),
                row.get("updated_at", Instant.class));
    }

    private RoleGrantRecord mapGrant(Record row) {
        return new RoleGrantRecord(
                row.get("grant_id", UUID.class),
                row.get("tenant_id", String.class),
                row.get("principal_id", String.class),
                row.get("role_id", UUID.class),
                row.get("role_code", String.class),
                row.get("scope_type", String.class),
                row.get("scope_ref", String.class),
                RoleGrantRecord.GrantStatus.valueOf(row.get("grant_status", String.class)),
                RoleGrantRecord.GrantEffect.valueOf(row.get("grant_effect", String.class)),
                row.get("valid_from", Instant.class),
                row.get("valid_to", Instant.class),
                row.get("source_code", String.class),
                row.get("approval_ref", String.class),
                row.get("requested_by", String.class),
                row.get("request_reason", String.class),
                row.get("approved_by", String.class),
                row.get("approved_at", Instant.class),
                row.get("rejected_by", String.class),
                row.get("rejected_at", Instant.class),
                row.get("reject_reason", String.class),
                row.get("revoked_at", Instant.class),
                row.get("revoked_by", String.class),
                row.get("revoke_reason", String.class),
                row.get("aggregate_version", Long.class),
                row.get("created_at", Instant.class),
                row.get("updated_at", Instant.class));
    }

    private DelegationRecord mapDelegationWithoutCapabilities(Record row) {
        return new DelegationRecord(
                row.get("delegation_id", UUID.class),
                row.get("tenant_id", String.class),
                row.get("delegator_principal_id", String.class),
                row.get("delegate_principal_id", String.class),
                new ArrayList<>(),
                row.get("scope_type", String.class),
                row.get("scope_ref", String.class),
                row.get("valid_from", Instant.class),
                row.get("valid_to", Instant.class),
                row.get("reason", String.class),
                DelegationRecord.Status.valueOf(row.get("delegation_status", String.class)),
                row.get("aggregate_version", Long.class),
                row.get("created_at", Instant.class),
                row.get("updated_at", Instant.class),
                row.get("revoked_at", Instant.class),
                row.get("revoked_by", String.class),
                row.get("revoke_reason", String.class));
    }
}
