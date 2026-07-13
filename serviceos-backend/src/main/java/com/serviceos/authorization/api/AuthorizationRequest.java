package com.serviceos.authorization.api;

/**
 * 动作授权输入。首切片先执行租户级 scope，后续字段保留稳定资源语义而非任意 SQL。
 */
public record AuthorizationRequest(
        String capability,
        String tenantId,
        String resourceType,
        String resourceId,
        String projectId,
        String organizationId,
        String regionCode,
        String networkId
) {
    public static AuthorizationRequest tenantCapability(
            String capability,
            String tenantId,
            String resourceType,
            String resourceId
    ) {
        return new AuthorizationRequest(
                capability, tenantId, resourceType, resourceId,
                null, null, null, null);
    }
}
