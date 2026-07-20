package com.serviceos.identity.application;

import com.serviceos.identity.api.IdentityLinkView;
import com.serviceos.identity.api.PrincipalLoginEventView;
import com.serviceos.identity.api.PrincipalPersonaView;
import com.serviceos.identity.domain.SecurityPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 主体目录命令持久化端口。认证登记使用 PostgreSQL 事务 advisory lock 串行化同一外部身份，
 * 避免并发首次登录产生孤儿 Principal 或半绑定事实。
 */
public interface IdentityDirectoryRepository {
    void lockIdentityKey(String tenantId, String issuer, String subject);

    Optional<SecurityPrincipal> findByExternalIdentity(String tenantId, String issuer, String subject);

    Optional<SecurityPrincipal> findById(String tenantId, UUID principalId);

    Optional<SecurityPrincipal> findByIdForUpdate(String tenantId, UUID principalId);

    void insertPrincipal(SecurityPrincipal principal, String actorId);

    void insertIdentityLink(
            UUID linkId, String tenantId, UUID principalId, String issuer, String subject,
            String clientId, String actorId, Instant now);

    boolean advanceLifecycle(
            String tenantId, UUID principalId, long expectedVersion, String status,
            String actorId, String reason, Instant now);

    boolean advanceVersion(String tenantId, UUID principalId, long expectedVersion, Instant now);

    boolean updateProfile(
            String tenantId, UUID principalId, long expectedPrincipalVersion,
            String displayName, String employeeNumber, String actorId, Instant now);

    boolean addPersonaAndAdvance(
            String tenantId, UUID principalId, long expectedPrincipalVersion,
            UUID personaId, String personaType, Instant validFrom, Instant validTo,
            String actorId, Instant now);

    void insertLifecycleEvent(
            UUID eventId, String tenantId, UUID principalId, String eventType,
            long principalVersion, String reason, String actorId, String requestDigest,
            String correlationId, Instant now);

    List<IdentityLinkView> findIdentityLinks(String tenantId, UUID principalId);

    List<PrincipalPersonaView> findPersonas(String tenantId, UUID principalId);

    Optional<PrincipalPersonaView> findPersona(String tenantId, UUID personaId);

    void insertLoginEvent(
            UUID loginEventId,
            String tenantId,
            UUID principalId,
            String clientId,
            String issuer,
            String correlationId,
            Instant occurredAt
    );

    void trimLoginEvents(String tenantId, UUID principalId, int keepLatest);

    List<PrincipalLoginEventView> listLoginEvents(
            String tenantId, UUID principalId, int limit);

    Optional<Instant> findLatestLoginAt(String tenantId, UUID principalId);
}
