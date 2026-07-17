package com.serviceos.network.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 列出本网点师傅服务关系。无目录 network.read 门禁；调用方负责 Portal 鉴权。
 *
 * <p>与 {@link NetworkPortalTechnicianQuery} / {@link NetworkPortalQualificationQuery} 同约定：
 * 本端口只做目录 fan-in，不校验 Portal membership / capability。</p>
 */
public interface NetworkPortalMembershipQuery {
    /**
     * 返回指定网点上的全部 NetworkTechnicianMembership（含 TERMINATED）；不做 status 过滤。
     */
    List<NetworkTechnicianMembershipView> listForNetwork(String tenantId, UUID networkId);

    /**
     * 按关系 ID 读取。不校验是否属于某网点；Portal 编排层须再核 serviceNetworkId。
     */
    Optional<NetworkTechnicianMembershipView> findById(String tenantId, UUID membershipId);
}
