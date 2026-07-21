package com.serviceos.project.infrastructure;

import com.serviceos.project.api.ProjectNetworkDirectoryQuery;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.PrjProjectNetwork.PRJ_PROJECT_NETWORK;

/** 项目目录 → 有效 NETWORK 绑定。 */
@Component
final class JooqProjectNetworkDirectoryQuery implements ProjectNetworkDirectoryQuery {
    private final DSLContext dsl;

    JooqProjectNetworkDirectoryQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<String> listActiveNetworkIds(String tenantId, UUID projectId, Instant asOf) {
        String safeTenant = Objects.requireNonNull(tenantId, "tenantId").trim();
        Objects.requireNonNull(projectId, "projectId");
        Instant evaluatedAt = Objects.requireNonNull(asOf, "asOf");
        var n = PRJ_PROJECT_NETWORK;
        return List.copyOf(dsl.select(n.NETWORK_ID)
                .from(n)
                .where(n.TENANT_ID.eq(safeTenant))
                .and(n.PROJECT_ID.eq(projectId))
                .and(n.VALID_FROM.le(evaluatedAt))
                .and(n.VALID_TO.isNull().or(n.VALID_TO.gt(evaluatedAt)))
                .orderBy(n.NETWORK_ID.asc())
                .fetch(n.NETWORK_ID));
    }
}
