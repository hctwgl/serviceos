package com.serviceos.readmodel.api;

import java.util.UUID;

/** Admin 服务网点页面使用的合作服务商选项。 */
public record AdminPartnerOrganizationDirectoryItem(
        UUID id,
        String partnerCode,
        String partnerName,
        String status
) {
}
