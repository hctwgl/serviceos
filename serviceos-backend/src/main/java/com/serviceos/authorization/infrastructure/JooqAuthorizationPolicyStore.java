package com.serviceos.authorization.infrastructure;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.application.AuthorizationPolicyStore;
import com.serviceos.authorization.application.CapabilityGrantMatch;
import com.serviceos.authorization.application.ProjectScopeGrantMatch;
import com.serviceos.authorization.application.ProjectScopePolicyStore;
import com.serviceos.jooq.generated.tables.AuthCapability;
import com.serviceos.jooq.generated.tables.AuthDelegation;
import com.serviceos.jooq.generated.tables.AuthDelegationCapability;
import com.serviceos.jooq.generated.tables.AuthRole;
import com.serviceos.jooq.generated.tables.AuthRoleCapability;
import com.serviceos.jooq.generated.tables.AuthRoleGrant;
import com.serviceos.jooq.generated.tables.AuthTenantGrantGeneration;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.serviceos.jooq.generated.tables.AuthCapability.AUTH_CAPABILITY;
import static com.serviceos.jooq.generated.tables.AuthDelegation.AUTH_DELEGATION;
import static com.serviceos.jooq.generated.tables.AuthDelegationCapability.AUTH_DELEGATION_CAPABILITY;
import static com.serviceos.jooq.generated.tables.AuthRole.AUTH_ROLE;
import static com.serviceos.jooq.generated.tables.AuthRoleCapability.AUTH_ROLE_CAPABILITY;
import static com.serviceos.jooq.generated.tables.AuthRoleGrant.AUTH_ROLE_GRANT;
import static com.serviceos.jooq.generated.tables.AuthTenantGrantGeneration.AUTH_TENANT_GRANT_GENERATION;
import static org.jooq.impl.DSL.inline;

/**
 * RoleGrant/Delegation 权威查询。仅 ACTIVE 未过期未撤销授权参与判定；匹配范围内 DENY 优先于 ALLOW；
 * policyVersion 绑定租户 grant generation，撤销后使依赖旧版本的上下文失败关闭。
 */
@Repository
final class JooqAuthorizationPolicyStore implements AuthorizationPolicyStore, ProjectScopePolicyStore {
    private final DSLContext dsl;

    JooqAuthorizationPolicyStore(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public CapabilityGrantMatch findCapabilityGrants(
            String tenantId,
            String principalId,
            AuthorizationRequest request,
            Instant evaluatedAt
    ) {
        String policyVersion = policyVersion(tenantId);
        AuthRoleGrant g = AUTH_ROLE_GRANT.as("g");
        AuthRole r = AUTH_ROLE.as("r");
        AuthRoleCapability rc = AUTH_ROLE_CAPABILITY.as("rc");
        AuthCapability c = AUTH_CAPABILITY.as("c");
        // match_id 原为 grant_id::text；UUID.toString() 与 PostgreSQL uuid 文本输出同为规范小写形式。
        List<MatchedGrantRow> grantRows = dsl.select(
                        g.GRANT_ID,
                        g.SCOPE_TYPE,
                        g.SCOPE_REF,
                        g.GRANT_EFFECT)
                .from(g)
                .join(r).on(r.ROLE_ID.eq(g.ROLE_ID))
                .join(rc).on(rc.ROLE_ID.eq(g.ROLE_ID))
                .join(c).on(c.CAPABILITY_CODE.eq(rc.CAPABILITY_CODE))
                .where(g.TENANT_ID.eq(tenantId))
                .and(g.PRINCIPAL_ID.eq(principalId))
                .and(c.CAPABILITY_CODE.eq(request.capability()))
                .and(r.TENANT_ID.eq(tenantId))
                .and(r.ROLE_STATUS.eq("ACTIVE"))
                .and(g.GRANT_STATUS.eq("ACTIVE"))
                .and(g.REVOKED_AT.isNull())
                .and(g.SCOPE_TYPE.eq("TENANT").and(g.SCOPE_REF.eq(tenantId))
                        .or(g.SCOPE_TYPE.eq("PROJECT").and(g.SCOPE_REF.eq(nullSafeScope(request.projectId()))))
                        .or(g.SCOPE_TYPE.eq("REGION").and(g.SCOPE_REF.eq(nullSafeScope(request.regionCode()))))
                        .or(g.SCOPE_TYPE.eq("NETWORK").and(g.SCOPE_REF.eq(nullSafeScope(request.networkId())))))
                .and(g.VALID_FROM.le(evaluatedAt))
                .and(g.VALID_TO.isNull().or(g.VALID_TO.gt(evaluatedAt)))
                .orderBy(g.GRANT_ID)
                .fetch(row -> new MatchedGrantRow(
                        row.get(g.GRANT_ID).toString(),
                        row.get(g.SCOPE_TYPE),
                        row.get(g.SCOPE_REF),
                        row.get(g.GRANT_EFFECT)));

        List<MatchedGrantRow> denyRows = grantRows.stream()
                .filter(row -> "DENY".equals(row.effect()))
                .toList();
        if (!denyRows.isEmpty()) {
            return new CapabilityGrantMatch(
                    false,
                    denyRows.stream().map(MatchedGrantRow::matchId).toList(),
                    denyRows.stream().map(row -> row.scopeType() + ":" + row.scopeRef()).toList(),
                    policyVersion);
        }

        List<MatchedGrantRow> allowRows = new ArrayList<>(grantRows.stream()
                .filter(row -> "ALLOW".equals(row.effect()))
                .toList());

        AuthDelegation d = AUTH_DELEGATION.as("d");
        AuthDelegationCapability dc = AUTH_DELEGATION_CAPABILITY.as("dc");
        List<MatchedGrantRow> delegationRows = dsl.select(
                        d.DELEGATION_ID,
                        d.SCOPE_TYPE,
                        d.SCOPE_REF)
                .from(d)
                .join(dc).on(dc.DELEGATION_ID.eq(d.DELEGATION_ID))
                .where(d.TENANT_ID.eq(tenantId))
                .and(d.DELEGATE_PRINCIPAL_ID.eq(principalId))
                .and(dc.CAPABILITY_CODE.eq(request.capability()))
                .and(d.DELEGATION_STATUS.eq("ACTIVE"))
                .and(d.REVOKED_AT.isNull())
                .and(d.SCOPE_TYPE.eq("TENANT").and(d.SCOPE_REF.eq(tenantId))
                        .or(d.SCOPE_TYPE.eq("PROJECT").and(d.SCOPE_REF.eq(nullSafeScope(request.projectId()))))
                        .or(d.SCOPE_TYPE.eq("REGION").and(d.SCOPE_REF.eq(nullSafeScope(request.regionCode()))))
                        .or(d.SCOPE_TYPE.eq("NETWORK").and(d.SCOPE_REF.eq(nullSafeScope(request.networkId())))))
                .and(d.VALID_FROM.le(evaluatedAt))
                .and(d.VALID_TO.isNull().or(d.VALID_TO.gt(evaluatedAt)))
                .orderBy(d.DELEGATION_ID)
                .fetch(row -> new MatchedGrantRow(
                        "delegation:" + row.get(d.DELEGATION_ID).toString(),
                        row.get(d.SCOPE_TYPE),
                        row.get(d.SCOPE_REF),
                        "ALLOW"));
        allowRows.addAll(delegationRows);

        if (allowRows.isEmpty()) {
            return CapabilityGrantMatch.denied(policyVersion);
        }
        return new CapabilityGrantMatch(
                true,
                allowRows.stream().map(MatchedGrantRow::matchId).toList(),
                allowRows.stream().map(row -> row.scopeType() + ":" + row.scopeRef()).toList(),
                policyVersion);
    }

    @Override
    public ProjectScopeGrantMatch findProjectScopeGrants(
            String tenantId, String principalId, String capability, Instant evaluatedAt
    ) {
        String policyVersion = policyVersion(tenantId);
        AuthRoleGrant g = AUTH_ROLE_GRANT.as("g");
        AuthRole r = AUTH_ROLE.as("r");
        AuthRoleCapability rc = AUTH_ROLE_CAPABILITY.as("rc");
        List<String> grantScopes = dsl.select(g.SCOPE_TYPE, g.SCOPE_REF)
                .from(g)
                .join(r).on(r.ROLE_ID.eq(g.ROLE_ID))
                .join(rc).on(rc.ROLE_ID.eq(g.ROLE_ID))
                .where(g.TENANT_ID.eq(tenantId))
                .and(g.PRINCIPAL_ID.eq(principalId))
                .and(rc.CAPABILITY_CODE.eq(capability))
                .and(r.TENANT_ID.eq(tenantId))
                .and(r.ROLE_STATUS.eq("ACTIVE"))
                .and(g.GRANT_STATUS.eq("ACTIVE"))
                .and(g.GRANT_EFFECT.eq("ALLOW"))
                .and(g.REVOKED_AT.isNull())
                .and(g.VALID_FROM.le(evaluatedAt))
                .and(g.VALID_TO.isNull().or(g.VALID_TO.gt(evaluatedAt)))
                .orderBy(g.SCOPE_TYPE, g.SCOPE_REF, g.GRANT_ID)
                .fetch(row -> row.get(g.SCOPE_TYPE) + ":" + row.get(g.SCOPE_REF));
        AuthDelegation d = AUTH_DELEGATION.as("d");
        AuthDelegationCapability dc = AUTH_DELEGATION_CAPABILITY.as("dc");
        List<String> delegationScopes = dsl.select(d.SCOPE_TYPE, d.SCOPE_REF)
                .from(d)
                .join(dc).on(dc.DELEGATION_ID.eq(d.DELEGATION_ID))
                .where(d.TENANT_ID.eq(tenantId))
                .and(d.DELEGATE_PRINCIPAL_ID.eq(principalId))
                .and(dc.CAPABILITY_CODE.eq(capability))
                .and(d.DELEGATION_STATUS.eq("ACTIVE"))
                .and(d.REVOKED_AT.isNull())
                .and(d.VALID_FROM.le(evaluatedAt))
                .and(d.VALID_TO.isNull().or(d.VALID_TO.gt(evaluatedAt)))
                .orderBy(d.SCOPE_TYPE, d.SCOPE_REF, d.DELEGATION_ID)
                .fetch(row -> row.get(d.SCOPE_TYPE) + ":" + row.get(d.SCOPE_REF));
        Set<String> scopes = new LinkedHashSet<>(grantScopes);
        scopes.addAll(delegationScopes);
        return new ProjectScopeGrantMatch(List.copyOf(scopes), policyVersion);
    }

    @Override
    public List<String> listEffectiveCapabilityCodes(
            String tenantId,
            String principalId,
            String scopeType,
            String scopeRef,
            Instant evaluatedAt
    ) {
        AuthRoleGrant g = AUTH_ROLE_GRANT.as("g");
        AuthRole r = AUTH_ROLE.as("r");
        AuthRoleCapability rc = AUTH_ROLE_CAPABILITY.as("rc");
        AuthCapability c = AUTH_CAPABILITY.as("c");
        AuthDelegation d = AUTH_DELEGATION.as("d");
        AuthDelegationCapability dc = AUTH_DELEGATION_CAPABILITY.as("dc");
        List<CapabilityEffectRow> rows = dsl.select(c.CAPABILITY_CODE, g.GRANT_EFFECT)
                .from(g)
                .join(r).on(r.ROLE_ID.eq(g.ROLE_ID))
                .join(rc).on(rc.ROLE_ID.eq(g.ROLE_ID))
                .join(c).on(c.CAPABILITY_CODE.eq(rc.CAPABILITY_CODE))
                .where(g.TENANT_ID.eq(tenantId))
                .and(g.PRINCIPAL_ID.eq(principalId))
                .and(r.TENANT_ID.eq(tenantId))
                .and(r.ROLE_STATUS.eq("ACTIVE"))
                .and(g.GRANT_STATUS.eq("ACTIVE"))
                .and(g.REVOKED_AT.isNull())
                .and(g.SCOPE_TYPE.eq(scopeType))
                .and(g.SCOPE_REF.eq(scopeRef))
                .and(g.VALID_FROM.le(evaluatedAt))
                .and(g.VALID_TO.isNull().or(g.VALID_TO.gt(evaluatedAt)))
                .unionAll(dsl.select(dc.CAPABILITY_CODE, inline("ALLOW"))
                        .from(d)
                        .join(dc).on(dc.DELEGATION_ID.eq(d.DELEGATION_ID))
                        .where(d.TENANT_ID.eq(tenantId))
                        .and(d.DELEGATE_PRINCIPAL_ID.eq(principalId))
                        .and(d.DELEGATION_STATUS.eq("ACTIVE"))
                        .and(d.REVOKED_AT.isNull())
                        .and(d.SCOPE_TYPE.eq(scopeType))
                        .and(d.SCOPE_REF.eq(scopeRef))
                        .and(d.VALID_FROM.le(evaluatedAt))
                        .and(d.VALID_TO.isNull().or(d.VALID_TO.gt(evaluatedAt))))
                .fetch(row -> new CapabilityEffectRow(row.value1(), row.value2()));

        Set<String> denied = new LinkedHashSet<>();
        Set<String> allowed = new LinkedHashSet<>();
        for (CapabilityEffectRow row : rows) {
            if ("DENY".equals(row.effect())) {
                denied.add(row.capabilityCode());
            } else if ("ALLOW".equals(row.effect())) {
                allowed.add(row.capabilityCode());
            }
        }

        // 运行时 TENANT 范围授权可覆盖 PROJECT/REGION/NETWORK 请求；导航能力集合同步并入。
        // 业务命令仍按具体资源重新鉴权，本集合不是授权凭证。
        if (!"TENANT".equals(scopeType)) {
            mergeEffects(allowed, denied, dsl.select(c.CAPABILITY_CODE, g.GRANT_EFFECT)
                    .from(g)
                    .join(r).on(r.ROLE_ID.eq(g.ROLE_ID))
                    .join(rc).on(rc.ROLE_ID.eq(g.ROLE_ID))
                    .join(c).on(c.CAPABILITY_CODE.eq(rc.CAPABILITY_CODE))
                    .where(g.TENANT_ID.eq(tenantId))
                    .and(g.PRINCIPAL_ID.eq(principalId))
                    .and(r.ROLE_STATUS.eq("ACTIVE"))
                    .and(g.GRANT_STATUS.eq("ACTIVE"))
                    .and(g.REVOKED_AT.isNull())
                    .and(g.SCOPE_TYPE.eq("TENANT"))
                    .and(g.SCOPE_REF.eq(tenantId))
                    .and(g.VALID_FROM.le(evaluatedAt))
                    .and(g.VALID_TO.isNull().or(g.VALID_TO.gt(evaluatedAt)))
                    .fetch(row -> new CapabilityEffectRow(row.value1(), row.value2())));
        } else {
            List<String> projectRegion = dsl.selectDistinct(c.CAPABILITY_CODE)
                    .from(g)
                    .join(r).on(r.ROLE_ID.eq(g.ROLE_ID))
                    .join(rc).on(rc.ROLE_ID.eq(g.ROLE_ID))
                    .join(c).on(c.CAPABILITY_CODE.eq(rc.CAPABILITY_CODE))
                    .where(g.TENANT_ID.eq(tenantId))
                    .and(g.PRINCIPAL_ID.eq(principalId))
                    .and(r.ROLE_STATUS.eq("ACTIVE"))
                    .and(g.GRANT_STATUS.eq("ACTIVE"))
                    .and(g.GRANT_EFFECT.eq("ALLOW"))
                    .and(g.REVOKED_AT.isNull())
                    .and(g.SCOPE_TYPE.in("PROJECT", "REGION"))
                    .and(g.VALID_FROM.le(evaluatedAt))
                    .and(g.VALID_TO.isNull().or(g.VALID_TO.gt(evaluatedAt)))
                    .fetch(c.CAPABILITY_CODE);
            allowed.addAll(projectRegion);
        }
        allowed.removeAll(denied);
        return List.copyOf(allowed);
    }

    private static void mergeEffects(
            Set<String> allowed, Set<String> denied, List<CapabilityEffectRow> rows
    ) {
        for (CapabilityEffectRow row : rows) {
            if ("DENY".equals(row.effect())) {
                denied.add(row.capabilityCode());
            } else if ("ALLOW".equals(row.effect())) {
                allowed.add(row.capabilityCode());
            }
        }
    }

    @Override
    public String policyVersion(String tenantId) {
        AuthTenantGrantGeneration t = AUTH_TENANT_GRANT_GENERATION;
        long generation = dsl.select(t.GENERATION)
                .from(t)
                .where(t.TENANT_ID.eq(tenantId))
                .fetchOptional(t.GENERATION)
                .orElse(0L);
        return "role-grant-v3:g" + generation;
    }

    private record CapabilityEffectRow(String capabilityCode, String effect) {
    }

    private static String nullSafeScope(String value) {
        return value == null ? "" : value;
    }

    private record MatchedGrantRow(String matchId, String scopeType, String scopeRef, String effect) {
    }
}
