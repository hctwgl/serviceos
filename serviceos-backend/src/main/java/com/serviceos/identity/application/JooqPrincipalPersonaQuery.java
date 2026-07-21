package com.serviceos.identity.application;

import com.serviceos.identity.api.PrincipalPersonaQuery;
import com.serviceos.identity.api.PrincipalPersonaView;
import com.serviceos.jooq.generated.tables.IdnPersonProfile;
import com.serviceos.jooq.generated.tables.IdnPrincipalPersona;
import com.serviceos.jooq.generated.tables.IdnSecurityPrincipal;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.IdnPersonProfile.IDN_PERSON_PROFILE;
import static com.serviceos.jooq.generated.tables.IdnPrincipalPersona.IDN_PRINCIPAL_PERSONA;
import static com.serviceos.jooq.generated.tables.IdnSecurityPrincipal.IDN_SECURITY_PRINCIPAL;

/** Principal Persona 有效性查询：供 authorization Portal 上下文使用，不绕过启停失权。 */
@Service
final class JooqPrincipalPersonaQuery implements PrincipalPersonaQuery {
    private final DSLContext dsl;

    JooqPrincipalPersonaQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean isActive(String tenantId, UUID principalId) {
        IdnSecurityPrincipal p = IDN_SECURITY_PRINCIPAL;
        return dsl.select(p.PRINCIPAL_STATUS.eq("ACTIVE"))
                .from(p)
                .where(p.TENANT_ID.eq(tenantId))
                .and(p.PRINCIPAL_ID.eq(principalId))
                .fetchOptional()
                .map(Record1::value1)
                .orElse(false);
    }

    @Override
    public Optional<String> displayName(String tenantId, UUID principalId) {
        IdnPersonProfile profile = IDN_PERSON_PROFILE;
        return dsl.select(profile.DISPLAY_NAME)
                .from(profile)
                .where(profile.TENANT_ID.eq(tenantId))
                .and(profile.PRINCIPAL_ID.eq(principalId))
                .fetchOptional(profile.DISPLAY_NAME);
    }

    @Override
    public List<PrincipalPersonaView> listEffectivePersonas(String tenantId, UUID principalId, Instant at) {
        IdnPrincipalPersona persona = IDN_PRINCIPAL_PERSONA;
        return dsl.select(persona.PERSONA_ID, persona.PERSONA_TYPE, persona.PERSONA_STATUS,
                        persona.VALID_FROM, persona.VALID_TO, persona.PERSONA_VERSION)
                .from(persona)
                .where(persona.TENANT_ID.eq(tenantId))
                .and(persona.PRINCIPAL_ID.eq(principalId))
                .and(persona.PERSONA_STATUS.eq("ACTIVE"))
                .and(persona.VALID_FROM.le(at))
                .and(persona.VALID_TO.isNull().or(persona.VALID_TO.gt(at)))
                .orderBy(persona.PERSONA_TYPE, persona.PERSONA_ID)
                .fetch(record -> new PrincipalPersonaView(
                        record.value1(), record.value2(), record.value3(),
                        record.value4(), record.value5(), record.value6()));
    }
}
