package com.serviceos.authorization.infrastructure;

import com.serviceos.authorization.application.PageRegistryOverrideStore;
import com.serviceos.jooq.generated.tables.AuthFeatureGate;
import com.serviceos.jooq.generated.tables.AuthPageRegistryOverride;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.serviceos.jooq.generated.tables.AuthFeatureGate.AUTH_FEATURE_GATE;
import static com.serviceos.jooq.generated.tables.AuthPageRegistryOverride.AUTH_PAGE_REGISTRY_OVERRIDE;

@Repository
final class JooqPageRegistryOverrideStore implements PageRegistryOverrideStore {
    private final DSLContext dsl;

    JooqPageRegistryOverrideStore(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Map<String, PageOverride> overridesForTenant(String tenantId) {
        AuthPageRegistryOverride t = AUTH_PAGE_REGISTRY_OVERRIDE;
        Map<String, PageOverride> result = new HashMap<>();
        dsl.select(t.PAGE_ID, t.ENABLED, t.TITLE_OVERRIDE, t.SORT_ORDER, t.FEATURE_GATE)
                .from(t)
                .where(t.TENANT_ID.eq(tenantId))
                .fetch(row -> new PageOverride(
                        row.get(t.PAGE_ID),
                        row.get(t.ENABLED),
                        Optional.ofNullable(row.get(t.TITLE_OVERRIDE)),
                        Optional.ofNullable(row.get(t.SORT_ORDER)),
                        Optional.ofNullable(row.get(t.FEATURE_GATE))))
                .forEach(override -> result.put(override.pageId(), override));
        return Map.copyOf(result);
    }

    @Override
    public Set<String> enabledFeatureGates(String tenantId) {
        AuthFeatureGate g = AUTH_FEATURE_GATE;
        Set<String> gates = new HashSet<>(dsl.select(g.GATE_CODE)
                .from(g)
                .where(g.TENANT_ID.eq(tenantId))
                .and(g.ENABLED.isTrue())
                .fetch(g.GATE_CODE));
        return Set.copyOf(gates);
    }
}
