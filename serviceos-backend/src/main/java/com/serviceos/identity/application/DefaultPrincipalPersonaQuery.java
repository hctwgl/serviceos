package com.serviceos.identity.application;

import com.serviceos.identity.api.PrincipalPersonaQuery;
import com.serviceos.identity.api.PrincipalPersonaView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/** Principal Persona 有效性查询：供 authorization Portal 上下文使用，不绕过启停失权。 */
@Service
final class DefaultPrincipalPersonaQuery implements PrincipalPersonaQuery {
    private final JdbcClient jdbc;

    DefaultPrincipalPersonaQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isActive(String tenantId, UUID principalId) {
        return Boolean.TRUE.equals(jdbc.sql("""
                        SELECT principal_status = 'ACTIVE'
                          FROM idn_security_principal
                         WHERE tenant_id=:tenant AND principal_id=:principalId
                        """)
                .param("tenant", tenantId).param("principalId", principalId)
                .query(Boolean.class).optional().orElse(false));
    }

    @Override
    public Optional<String> displayName(String tenantId, UUID principalId) {
        return jdbc.sql("""
                        SELECT display_name
                          FROM idn_person_profile
                         WHERE tenant_id=:tenant AND principal_id=:principalId
                        """)
                .param("tenant", tenantId).param("principalId", principalId)
                .query(String.class).optional();
    }

    @Override
    public Map<UUID, String> displayNames(String tenantId, Collection<UUID> principalIds) {
        if (principalIds == null || principalIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        jdbc.sql("""
                SELECT principal_id, display_name
                  FROM idn_person_profile
                 WHERE tenant_id = :tenant
                   AND principal_id IN (:ids)
                """)
                .param("tenant", tenantId)
                .param("ids", principalIds)
                .query((rs, rowNum) -> {
                    names.put(rs.getObject("principal_id", UUID.class), rs.getString("display_name"));
                    return null;
                })
                .list();
        return names;
    }

    @Override
    public List<PrincipalPersonaView> listEffectivePersonas(String tenantId, UUID principalId, Instant at) {
        return jdbc.sql("""
                        SELECT persona_id, persona_type, persona_status, valid_from, valid_to, persona_version
                          FROM idn_principal_persona
                         WHERE tenant_id=:tenant
                           AND principal_id=:principalId
                           AND persona_status='ACTIVE'
                           AND valid_from <= :at
                           AND (valid_to IS NULL OR valid_to > :at)
                         ORDER BY persona_type, persona_id
                        """)
                .param("tenant", tenantId)
                .param("principalId", principalId)
                .param("at", timestamptz(at))
                .query((rs, row) -> new PrincipalPersonaView(
                        rs.getObject("persona_id", UUID.class),
                        rs.getString("persona_type"),
                        rs.getString("persona_status"),
                        rs.getObject("valid_from", OffsetDateTime.class).toInstant(),
                        Optional.ofNullable(rs.getObject("valid_to", OffsetDateTime.class))
                                .map(OffsetDateTime::toInstant).orElse(null),
                        rs.getLong("persona_version")))
                .list();
    }
}
