package com.serviceos.authorization.infrastructure;

import com.serviceos.authorization.api.FieldAccessDecision;
import com.serviceos.authorization.api.FieldPermission;
import com.serviceos.authorization.application.FieldPolicyMatch;
import com.serviceos.authorization.application.FieldPolicyStore;
import com.serviceos.jooq.generated.tables.AuthFieldPolicy;
import com.serviceos.jooq.generated.tables.AuthFieldPolicyRule;
import com.serviceos.jooq.generated.tables.AuthRoleFieldPolicy;
import com.serviceos.jooq.generated.tables.AuthRoleGrant;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.serviceos.jooq.generated.tables.AuthFieldPolicy.AUTH_FIELD_POLICY;
import static com.serviceos.jooq.generated.tables.AuthFieldPolicyRule.AUTH_FIELD_POLICY_RULE;
import static com.serviceos.jooq.generated.tables.AuthRoleFieldPolicy.AUTH_ROLE_FIELD_POLICY;
import static com.serviceos.jooq.generated.tables.AuthRoleGrant.AUTH_ROLE_GRANT;

/**
 * 已发布字段策略查询与确定性合并。
 *
 * <p>显式 HIDDEN 优先于任何允许；否则取权限等级最高的规则。规则不存在时由应用层默认 HIDDEN。
 * 该顺序可防止某个高权限角色意外绕过面向特定资源的显式隐藏策略。</p>
 */
@Repository
final class JooqFieldPolicyStore implements FieldPolicyStore {
    private static final String POLICY_VERSION = "field-policy-v1";
    private static final Map<FieldPermission, Integer> PERMISSION_RANK = Map.of(
            FieldPermission.MASKED, 1,
            FieldPermission.READ, 2,
            FieldPermission.WRITE, 3,
            FieldPermission.EXPORT, 4,
            FieldPermission.HIDDEN, 100);

    private final DSLContext dsl;

    JooqFieldPolicyStore(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public FieldPolicyMatch resolve(
            String tenantId,
            List<String> matchedGrantIds,
            String capability,
            String resourceType,
            Set<String> fieldCodes
    ) {
        if (matchedGrantIds.isEmpty()) {
            return new FieldPolicyMatch(Map.of(), POLICY_VERSION);
        }

        AuthRoleGrant grantRecord = AUTH_ROLE_GRANT.as("grant_record");
        AuthRoleFieldPolicy rolePolicy = AUTH_ROLE_FIELD_POLICY.as("role_policy");
        AuthFieldPolicy policy = AUTH_FIELD_POLICY.as("policy");
        AuthFieldPolicyRule rule = AUTH_FIELD_POLICY_RULE.as("rule");
        // matchedGrantIds 为 policyVersion 查询输出的 grant_id 文本形式；保持原 ::text 比较语义。
        List<FieldRuleRow> rows = dsl.select(
                        rule.FIELD_CODE, rule.ACCESS_LEVEL, rule.MASK_CODE,
                        policy.POLICY_CODE, policy.POLICY_VERSION)
                .from(grantRecord)
                .join(rolePolicy).on(rolePolicy.ROLE_ID.eq(grantRecord.ROLE_ID))
                .join(policy).on(policy.POLICY_ID.eq(rolePolicy.POLICY_ID))
                .join(rule).on(rule.POLICY_ID.eq(policy.POLICY_ID))
                .where(grantRecord.TENANT_ID.eq(tenantId))
                .and(grantRecord.GRANT_ID.cast(String.class).in(matchedGrantIds))
                .and(policy.TENANT_ID.eq(tenantId))
                .and(policy.RESOURCE_TYPE.eq(resourceType))
                .and(policy.POLICY_STATUS.eq("PUBLISHED"))
                .and(rule.CAPABILITY_CODE.eq(capability))
                .and(rule.FIELD_CODE.in(fieldCodes))
                .orderBy(rule.FIELD_CODE, policy.POLICY_CODE, policy.POLICY_VERSION)
                .fetch(row -> new FieldRuleRow(
                        row.get(rule.FIELD_CODE),
                        FieldPermission.valueOf(row.get(rule.ACCESS_LEVEL)),
                        row.get(rule.MASK_CODE),
                        row.get(policy.POLICY_CODE) + ":v" + row.get(policy.POLICY_VERSION)));

        Map<String, List<FieldRuleRow>> grouped = new LinkedHashMap<>();
        rows.forEach(row -> grouped.computeIfAbsent(row.fieldCode(), ignored -> new ArrayList<>()).add(row));
        LinkedHashMap<String, FieldAccessDecision> decisions = new LinkedHashMap<>();
        grouped.forEach((field, rules) -> decisions.put(field, mergeRules(rules)));
        return new FieldPolicyMatch(decisions, POLICY_VERSION);
    }

    private static FieldAccessDecision mergeRules(List<FieldRuleRow> rules) {
        if (rules.stream().anyMatch(rule -> rule.permission() == FieldPermission.HIDDEN)) {
            return FieldAccessDecision.hidden();
        }
        FieldRuleRow selected = rules.stream()
                .max(Comparator
                        .comparingInt((FieldRuleRow rule) -> PERMISSION_RANK.get(rule.permission()))
                        .thenComparing(FieldRuleRow::policyRef, Comparator.reverseOrder()))
                .orElseThrow();
        return new FieldAccessDecision(selected.permission(), selected.maskCode());
    }

    private record FieldRuleRow(
            String fieldCode,
            FieldPermission permission,
            String maskCode,
            String policyRef
    ) {
    }
}
