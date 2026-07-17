package com.serviceos.network.api;

import java.util.List;
import java.util.UUID;

/**
 * 列出网点 ACTIVE 师傅关系。无目录 network.read 门禁；调用方负责 Portal 鉴权。
 */
public interface NetworkPortalTechnicianQuery {
    List<NetworkPortalTechnicianView> listActiveTechnicians(String tenantId, UUID networkId);
}
