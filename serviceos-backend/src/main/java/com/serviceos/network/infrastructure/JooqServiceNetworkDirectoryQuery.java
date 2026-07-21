package com.serviceos.network.infrastructure;

import com.serviceos.jooq.generated.tables.NetServiceNetwork;
import com.serviceos.network.api.ServiceNetworkDirectoryQuery;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.serviceos.jooq.generated.tables.NetServiceNetwork.NET_SERVICE_NETWORK;

/** ACTIVE ServiceNetwork 过滤（service_network_id 转文本后按项目侧 network_id 文本匹配）。 */
@Component
final class JooqServiceNetworkDirectoryQuery implements ServiceNetworkDirectoryQuery {
    private final DSLContext dsl;

    JooqServiceNetworkDirectoryQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<String> listActiveNetworkIds(String tenantId, Collection<String> networkIds) {
        String safeTenant = Objects.requireNonNull(tenantId, "tenantId").trim();
        Objects.requireNonNull(networkIds, "networkIds");
        if (networkIds.isEmpty()) {
            return List.of();
        }
        NetServiceNetwork n = NET_SERVICE_NETWORK;
        // 与原 ::text 一致：cast as varchar 产生相同的 UUID 规范文本，保持文本匹配语义。
        Field<String> networkIdText = n.SERVICE_NETWORK_ID.cast(String.class);
        return List.copyOf(dsl.select(networkIdText)
                .from(n)
                .where(n.TENANT_ID.eq(safeTenant))
                .and(networkIdText.in(networkIds))
                .and(n.NETWORK_STATUS.eq("ACTIVE"))
                .orderBy(n.SERVICE_NETWORK_ID.asc())
                .fetch(networkIdText));
    }
}
