package com.serviceos.identity.infrastructure;

import com.serviceos.identity.api.IdentityLinkView;
import com.serviceos.identity.api.PrincipalPersonaView;
import com.serviceos.identity.application.IdentityDirectoryRepository;
import com.serviceos.identity.domain.SecurityPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 身份命令使用 Spring JDBC 的理由是必须精确控制 advisory lock、FOR UPDATE、影响行数和
 * `ON CONFLICT DO NOTHING` 并发语义；目录列表仍由独立 MyBatis XML 承担。
 */
@Repository
final class JdbcIdentityDirectoryRepository implements IdentityDirectoryRepository {
    private static final String PRINCIPAL_SELECT = """
            SELECT p.principal_id, p.tenant_id, p.principal_type, p.principal_status,
                   p.aggregate_version, p.created_at, p.updated_at,
                   p.disabled_at, p.disabled_by, p.disabled_reason,
                   profile.display_name, profile.employee_number, profile.profile_version
              FROM idn_security_principal p
              JOIN idn_person_profile profile
                ON profile.tenant_id=p.tenant_id AND profile.principal_id=p.principal_id
            """;

    private final JdbcClient jdbc;

    JdbcIdentityDirectoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lockIdentityKey(String tenantId, String issuer, String subject) {
        // 锁键包含 tenant，避免不同租户相同 issuer/subject 相互阻塞；hash 冲突只会造成短暂串行，不破坏正确性。
        jdbc.sql("""
                SELECT 1
                  FROM (SELECT pg_advisory_xact_lock(hashtextextended(:identityKey, 0))) locked
                """)
                .param("identityKey", tenantId + "\u001f" + issuer + "\u001f" + subject)
                .query(Integer.class)
                .single();
    }

    @Override
    public Optional<SecurityPrincipal> findByExternalIdentity(String tenantId, String issuer, String subject) {
        return jdbc.sql(PRINCIPAL_SELECT + """
                 JOIN idn_identity_link link
                   ON link.tenant_id=p.tenant_id AND link.principal_id=p.principal_id
                WHERE link.tenant_id=:tenantId AND link.issuer=:issuer AND link.subject_value=:subject
                """)
                .param("tenantId", tenantId).param("issuer", issuer).param("subject", subject)
                .query(this::mapPrincipal).optional();
    }

    @Override
    public Optional<SecurityPrincipal> findById(String tenantId, UUID principalId) {
        return principalQuery(tenantId, principalId, false);
    }

    @Override
    public Optional<SecurityPrincipal> findByIdForUpdate(String tenantId, UUID principalId) {
        return principalQuery(tenantId, principalId, true);
    }

    private Optional<SecurityPrincipal> principalQuery(String tenantId, UUID principalId, boolean forUpdate) {
        return jdbc.sql(PRINCIPAL_SELECT + " WHERE p.tenant_id=:tenantId AND p.principal_id=:principalId"
                        + (forUpdate ? " FOR UPDATE OF p" : ""))
                .param("tenantId", tenantId).param("principalId", principalId)
                .query(this::mapPrincipal).optional();
    }

    @Override
    public void insertPrincipal(SecurityPrincipal principal, String actorId) {
        jdbc.sql("""
                INSERT INTO idn_security_principal (
                    principal_id, tenant_id, principal_type, principal_status,
                    aggregate_version, created_at, updated_at
                ) VALUES (
                    :id, :tenant, :type, :status, :version, :createdAt, :updatedAt
                )
                """)
                .param("id", principal.id()).param("tenant", principal.tenantId())
                .param("type", principal.type().name()).param("status", principal.status().name())
                .param("version", principal.version()).param("createdAt", dbTime(principal.createdAt()))
                .param("updatedAt", dbTime(principal.updatedAt())).update();
        jdbc.sql("""
                INSERT INTO idn_person_profile (
                    principal_id, tenant_id, display_name, employee_number,
                    profile_version, created_at, updated_at, updated_by
                ) VALUES (
                    :id, :tenant, :displayName, :employeeNumber, 1, :now, :now, :actor
                )
                """)
                .param("id", principal.id()).param("tenant", principal.tenantId())
                .param("displayName", principal.displayName())
                .param("employeeNumber", principal.employeeNumber())
                .param("now", dbTime(principal.createdAt())).param("actor", actorId).update();
    }

    @Override
    public void insertIdentityLink(
            UUID linkId, String tenantId, UUID principalId, String issuer, String subject,
            String clientId, String actorId, Instant now
    ) {
        int inserted = jdbc.sql("""
                INSERT INTO idn_identity_link (
                    identity_link_id, tenant_id, principal_id, issuer, subject_value,
                    client_id, linked_by, linked_at
                ) VALUES (
                    :linkId, :tenant, :principalId, :issuer, :subject,
                    :clientId, :actor, :now
                )
                ON CONFLICT (tenant_id, issuer, subject_value) DO NOTHING
                """)
                .param("linkId", linkId).param("tenant", tenantId).param("principalId", principalId)
                .param("issuer", issuer).param("subject", subject).param("clientId", clientId)
                .param("actor", actorId).param("now", dbTime(now)).update();
        if (inserted != 1) {
            throw new BusinessProblem(ProblemCode.IDENTITY_LINK_CONFLICT, "外部身份已经绑定");
        }
    }

    @Override
    public boolean advanceLifecycle(
            String tenantId, UUID principalId, long expectedVersion, String status,
            String actorId, String reason, Instant now
    ) {
        return jdbc.sql("""
                UPDATE idn_security_principal
                   SET principal_status=:status,
                       aggregate_version=aggregate_version+1,
                       updated_at=:now,
                       disabled_at=CASE WHEN :status='DISABLED' THEN :now ELSE NULL END,
                       disabled_by=CASE WHEN :status='DISABLED' THEN :actor ELSE NULL END,
                       disabled_reason=CASE WHEN :status='DISABLED' THEN :reason ELSE NULL END
                 WHERE tenant_id=:tenant AND principal_id=:principalId
                   AND aggregate_version=:expectedVersion
                """)
                .param("status", status).param("now", dbTime(now)).param("actor", actorId).param("reason", reason)
                .param("tenant", tenantId).param("principalId", principalId)
                .param("expectedVersion", expectedVersion).update() == 1;
    }

    @Override
    public boolean advanceVersion(String tenantId, UUID principalId, long expectedVersion, Instant now) {
        return jdbc.sql("""
                UPDATE idn_security_principal
                   SET aggregate_version=aggregate_version+1, updated_at=:now
                 WHERE tenant_id=:tenant AND principal_id=:principalId
                   AND aggregate_version=:expectedVersion
                """)
                .param("now", dbTime(now)).param("tenant", tenantId).param("principalId", principalId)
                .param("expectedVersion", expectedVersion).update() == 1;
    }

    @Override
    public boolean updateProfile(
            String tenantId, UUID principalId, long expectedPrincipalVersion,
            String displayName, String employeeNumber, String actorId, Instant now
    ) {
        if (!advanceVersion(tenantId, principalId, expectedPrincipalVersion, now)) return false;
        try {
            int updated = jdbc.sql("""
                    UPDATE idn_person_profile
                       SET display_name=:displayName, employee_number=:employeeNumber,
                           profile_version=profile_version+1, updated_at=:now, updated_by=:actor
                     WHERE tenant_id=:tenant AND principal_id=:principalId
                    """)
                    .param("displayName", displayName).param("employeeNumber", employeeNumber)
                    .param("now", dbTime(now)).param("actor", actorId).param("tenant", tenantId)
                    .param("principalId", principalId).update();
            if (updated != 1) throw new IllegalStateException("主体 Profile 不存在");
            return true;
        } catch (DuplicateKeyException exception) {
            throw new BusinessProblem(ProblemCode.IDENTITY_PROFILE_CONFLICT, "员工编号已经被其他主体使用");
        }
    }

    @Override
    public boolean addPersonaAndAdvance(
            String tenantId, UUID principalId, long expectedPrincipalVersion,
            UUID personaId, String personaType, Instant validFrom, Instant validTo,
            String actorId, Instant now
    ) {
        int inserted = jdbc.sql("""
                INSERT INTO idn_principal_persona (
                    persona_id, tenant_id, principal_id, persona_type, persona_status,
                    valid_from, valid_to, persona_version, created_by, created_at
                ) VALUES (
                    :personaId, :tenant, :principalId, :personaType, 'ACTIVE',
                    :validFrom, :validTo, 1, :actor, :now
                )
                ON CONFLICT (tenant_id, principal_id, persona_type) DO NOTHING
                """)
                .param("personaId", personaId).param("tenant", tenantId).param("principalId", principalId)
                .param("personaType", personaType).param("validFrom", dbTime(validFrom))
                .param("validTo", validTo == null ? null : dbTime(validTo), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("actor", actorId).param("now", dbTime(now)).update();
        if (inserted != 1) {
            throw new BusinessProblem(ProblemCode.IDENTITY_PROFILE_CONFLICT, "主体已经具有该 Persona");
        }
        return advanceVersion(tenantId, principalId, expectedPrincipalVersion, now);
    }

    @Override
    public void insertLifecycleEvent(
            UUID eventId, String tenantId, UUID principalId, String eventType,
            long principalVersion, String reason, String actorId, String requestDigest,
            String correlationId, Instant now
    ) {
        jdbc.sql("""
                INSERT INTO idn_principal_lifecycle_event (
                    lifecycle_event_id, tenant_id, principal_id, event_type,
                    principal_version, reason, actor_id, request_digest,
                    correlation_id, occurred_at
                ) VALUES (
                    :eventId, :tenant, :principalId, :eventType,
                    :version, :reason, :actor, :digest, :correlationId, :now
                )
                """)
                .param("eventId", eventId).param("tenant", tenantId).param("principalId", principalId)
                .param("eventType", eventType).param("version", principalVersion).param("reason", reason)
                .param("actor", actorId).param("digest", requestDigest)
                .param("correlationId", correlationId).param("now", dbTime(now)).update();
    }

    @Override
    public List<IdentityLinkView> findIdentityLinks(String tenantId, UUID principalId) {
        return jdbc.sql("""
                SELECT identity_link_id, issuer, subject_value, client_id, linked_at
                  FROM idn_identity_link
                 WHERE tenant_id=:tenant AND principal_id=:principalId
                 ORDER BY linked_at, identity_link_id
                """)
                .param("tenant", tenantId).param("principalId", principalId)
                .query((rs, row) -> new IdentityLinkView(
                        rs.getObject("identity_link_id", UUID.class), rs.getString("issuer"),
                        rs.getString("subject_value"), rs.getString("client_id"),
                        rs.getObject("linked_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    @Override
    public List<PrincipalPersonaView> findPersonas(String tenantId, UUID principalId) {
        return jdbc.sql("""
                SELECT persona_id, persona_type, persona_status, valid_from, valid_to, persona_version
                  FROM idn_principal_persona
                 WHERE tenant_id=:tenant AND principal_id=:principalId
                 ORDER BY persona_type, persona_id
                """)
                .param("tenant", tenantId).param("principalId", principalId)
                .query((rs, row) -> new PrincipalPersonaView(
                        rs.getObject("persona_id", UUID.class), rs.getString("persona_type"),
                        rs.getString("persona_status"),
                        rs.getObject("valid_from", OffsetDateTime.class).toInstant(),
                        instantOrNull(rs.getObject("valid_to")), rs.getLong("persona_version")))
                .list();
    }

    @Override
    public Optional<PrincipalPersonaView> findPersona(String tenantId, UUID personaId) {
        return jdbc.sql("""
                SELECT persona_id AS "personaId", persona_type AS "personaType",
                       persona_status AS "personaStatus", valid_from AS "validFrom",
                       valid_to AS "validTo", persona_version AS "personaVersion"
                  FROM idn_principal_persona
                 WHERE tenant_id=:tenant AND persona_id=:personaId
                """)
                .param("tenant", tenantId).param("personaId", personaId)
                .query((rs, row) -> new PrincipalPersonaView(
                        rs.getObject("personaId", UUID.class), rs.getString("personaType"),
                        rs.getString("personaStatus"),
                        rs.getObject("validFrom", OffsetDateTime.class).toInstant(),
                        instantOrNull(rs.getObject("validTo")), rs.getLong("personaVersion")))
                .optional();
    }

    private SecurityPrincipal mapPrincipal(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new SecurityPrincipal(
                rs.getObject("principal_id", UUID.class), rs.getString("tenant_id"),
                SecurityPrincipal.Type.valueOf(rs.getString("principal_type")),
                SecurityPrincipal.Status.valueOf(rs.getString("principal_status")),
                rs.getLong("aggregate_version"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
                instantOrNull(rs.getObject("disabled_at")), rs.getString("disabled_by"),
                rs.getString("disabled_reason"), rs.getString("display_name"),
                rs.getString("employee_number"), rs.getLong("profile_version"));
    }

    private static Instant instantOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Instant instant) return instant;
        return Instant.parse(value.toString());
    }

    private static OffsetDateTime dbTime(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
