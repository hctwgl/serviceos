package com.serviceos.network.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 网点/师傅目录显示名批量查询端口。
 *
 * <p>供已授权目录旁载使用：调用方已完成业务授权，本端口不再二次鉴权，也不穿越租户。</p>
 */
public interface NetworkDirectoryLabelQuery {

    /**
     * @return networkId → networkName；缺档不出现
     */
    Map<UUID, String> findNetworkNames(String tenantId, Collection<UUID> networkIds);

    /**
     * @return technicianProfileId → displayName；缺档不出现
     */
    Map<UUID, String> findTechnicianProfileDisplayNames(
            String tenantId, Collection<UUID> technicianProfileIds);
}
