package com.serviceos.authorization.api;

/**
 * 动作授权输入。scope 只接受平台定义的稳定资源维度，不允许调用方传入 SQL 或任意表达式。
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

    public static AuthorizationRequest projectCapability(
            String capability,
            String tenantId,
            String resourceType,
            String resourceId,
            String projectId
    ) {
        return new AuthorizationRequest(
                capability, tenantId, resourceType, resourceId,
                projectId, null, null, null);
    }

    public static AuthorizationRequest regionCapability(
            String capability,
            String tenantId,
            String resourceType,
            String resourceId,
            String regionCode
    ) {
        return new AuthorizationRequest(
                capability, tenantId, resourceType, resourceId,
                null, null, regionCode, null);
    }

    public static AuthorizationRequest networkCapability(
            String capability,
            String tenantId,
            String resourceType,
            String resourceId,
            String networkId
    ) {
        return new AuthorizationRequest(
                capability, tenantId, resourceType, resourceId,
                null, null, null, networkId);
    }
}
