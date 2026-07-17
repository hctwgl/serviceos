package com.serviceos.authorization.infrastructure;

import com.serviceos.authorization.application.PageRegistryOverrideStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
final class JdbcPageRegistryOverrideStore implements PageRegistryOverrideStore {
    private final JdbcClient jdbc;

    JdbcPageRegistryOverrideStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, PageOverride> overridesForTenant(String tenantId) {
        Map<String, PageOverride> result = new HashMap<>();
        jdbc.sql("""
                        SELECT page_id, enabled, title_override, sort_order, feature_gate
                          FROM auth_page_registry_override
                         WHERE tenant_id = :tenantId
                        """)
                .param("tenantId", tenantId)
                .query((rs, rowNum) -> {
                    result.put(rs.getString("page_id"), new PageOverride(
                            rs.getString("page_id"),
                            rs.getBoolean("enabled"),
                            Optional.ofNullable(rs.getString("title_override")),
                            Optional.ofNullable(rs.getObject("sort_order", Integer.class)),
                            Optional.ofNullable(rs.getString("feature_gate"))));
                    return null;
                })
                .list();
        return Map.copyOf(result);
    }

    @Override
    public Set<String> enabledFeatureGates(String tenantId) {
        Set<String> gates = new HashSet<>(jdbc.sql("""
                        SELECT gate_code
                          FROM auth_feature_gate
                         WHERE tenant_id = :tenantId
                           AND enabled = TRUE
                        """)
                .param("tenantId", tenantId)
                .query(String.class)
                .list());
        return Set.copyOf(gates);
    }
}
