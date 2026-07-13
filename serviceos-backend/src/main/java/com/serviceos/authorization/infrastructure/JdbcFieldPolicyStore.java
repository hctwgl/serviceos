package com.serviceos.authorization.infrastructure;

import com.serviceos.authorization.api.FieldAccessDecision;
import com.serviceos.authorization.api.FieldPermission;
import com.serviceos.authorization.application.FieldPolicyMatch;
import com.serviceos.authorization.application.FieldPolicyStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 已发布字段策略查询与确定性合并。
 *
 * <p>显式 HIDDEN 优先于任何允许；否则取权限等级最高的规则。规则不存在时由应用层默认 HIDDEN。
 * 该顺序可防止某个高权限角色意外绕过面向特定资源的显式隐藏策略。</p>
 */
@Repository
final class JdbcFieldPolicyStore implements FieldPolicyStore {
    private static final String POLICY_VERSION = "field-policy-v1";
    private static final Map<FieldPermission, Integer> PERMISSION_RANK = Map.of(
            FieldPermission.MASKED, 1,
            FieldPermission.READ, 2,
            FieldPermission.WRITE, 3,
            FieldPermission.EXPORT, 4,
            FieldPermission.HIDDEN, 100);

    private final JdbcClient jdbc;

    JdbcFieldPolicyStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
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

        List<FieldRuleRow> rows = jdbc.sql("""
                        SELECT rule.field_code, rule.access_level, rule.mask_code,
                               policy.policy_code, policy.policy_version
                          FROM auth_role_grant grant_record
                          JOIN auth_role_field_policy role_policy
                            ON role_policy.role_id = grant_record.role_id
                          JOIN auth_field_policy policy
                            ON policy.policy_id = role_policy.policy_id
                          JOIN auth_field_policy_rule rule
                            ON rule.policy_id = policy.policy_id
                         WHERE grant_record.tenant_id = :tenantId
                           AND grant_record.grant_id::text IN (:grantIds)
                           AND policy.tenant_id = :tenantId
                           AND policy.resource_type = :resourceType
                           AND policy.policy_status = 'PUBLISHED'
                           AND rule.capability_code = :capability
                           AND rule.field_code IN (:fieldCodes)
                         ORDER BY rule.field_code, policy.policy_code, policy.policy_version
                        """)
                .param("tenantId", tenantId)
                .param("grantIds", matchedGrantIds)
                .param("resourceType", resourceType)
                .param("capability", capability)
                .param("fieldCodes", fieldCodes)
                .query((rs, rowNum) -> new FieldRuleRow(
                        rs.getString("field_code"),
                        FieldPermission.valueOf(rs.getString("access_level")),
                        rs.getString("mask_code"),
                        rs.getString("policy_code") + ":v" + rs.getInt("policy_version")))
                .list();

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
