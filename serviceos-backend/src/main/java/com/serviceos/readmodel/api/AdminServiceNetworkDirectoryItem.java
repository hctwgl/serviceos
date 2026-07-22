package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin 服务网点目录项。把合作组织、覆盖区域和在册师傅摘要一次性返回给页面，
 * 避免浏览器按内部标识拼接多个领域接口。
 */
public record AdminServiceNetworkDirectoryItem(
        UUID id,
        String networkCode,
        String networkName,
        String partnerOrganizationName,
        String status,
        List<String> regionCodes,
        int activeTechnicianCount,
        Instant updatedAt
) {
    public AdminServiceNetworkDirectoryItem {
        regionCodes = regionCodes == null ? List.of() : List.copyOf(regionCodes);
    }
}
