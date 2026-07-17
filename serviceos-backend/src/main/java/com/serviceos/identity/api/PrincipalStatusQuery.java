package com.serviceos.identity.api;

import java.util.UUID;

/**
 * 只读 Principal 状态查询，供 network 等模块判定主体是否 ACTIVE，不暴露身份目录写能力。
 */
public interface PrincipalStatusQuery {
    boolean isActive(String tenantId, UUID principalId);
}
