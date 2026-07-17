package com.serviceos.identity.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 只读 Persona/显示名查询，供 Portal 上下文计算使用；不暴露身份目录写能力或敏感 IdentityLink。
 */
public interface PrincipalPersonaQuery {
    boolean isActive(String tenantId, UUID principalId);

    Optional<String> displayName(String tenantId, UUID principalId);

    List<PrincipalPersonaView> listEffectivePersonas(String tenantId, UUID principalId, Instant at);
}
