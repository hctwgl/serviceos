package com.serviceos.identity.infrastructure;

import com.serviceos.identity.api.IdentityLinkView;
import com.serviceos.identity.api.PrincipalPersonaView;
import com.serviceos.identity.application.IdentityDirectoryRepository;
import com.serviceos.identity.domain.SecurityPrincipal;
import com.serviceos.jooq.generated.tables.IdnIdentityLink;
import com.serviceos.jooq.generated.tables.IdnPersonProfile;
import com.serviceos.jooq.generated.tables.IdnPrincipalLifecycleEvent;
import com.serviceos.jooq.generated.tables.IdnPrincipalPersona;
import com.serviceos.jooq.generated.tables.IdnSecurityPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record13;
import org.jooq.Record6;
import org.jooq.SelectJoinStep;
import org.jooq.SelectOnConditionStep;
import org.jooq.impl.DSL;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.IdnIdentityLink.IDN_IDENTITY_LINK;
import static com.serviceos.jooq.generated.tables.IdnPersonProfile.IDN_PERSON_PROFILE;
import static com.serviceos.jooq.generated.tables.IdnPrincipalLifecycleEvent.IDN_PRINCIPAL_LIFECYCLE_EVENT;
import static com.serviceos.jooq.generated.tables.IdnPrincipalPersona.IDN_PRINCIPAL_PERSONA;
import static com.serviceos.jooq.generated.tables.IdnSecurityPrincipal.IDN_SECURITY_PRINCIPAL;

/**
 * 身份命令依赖 PostgreSQL 精确并发语义（advisory lock、FOR UPDATE、影响行数校验、
 * `ON CONFLICT DO NOTHING`）；目录列表查询由 JooqIdentityDirectoryQueryRepository 承担。
 * jOOQ 生成类型把 timestamptz 统一映射为 Instant，旧实现的多类型时间容错转换随之移除。
 */
@Repository
final class JooqIdentityDirectoryRepository implements IdentityDirectoryRepository {
    private final DSLContext dsl;

    JooqIdentityDirectoryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void lockIdentityKey(String tenantId, String issuer, String subject) {
        // 锁键包含 tenant，避免不同租户相同 issuer/subject 相互阻塞；hash 冲突只会造成短暂串行，不破坏正确性。
        dsl.select(DSL.function("pg_advisory_xact_lock", Object.class,
                        DSL.function("hashtextextended", Long.class,
                                DSL.val(tenantId + "\u001f" + issuer + "\u001f" + subject), DSL.val(0L))))
                .fetchSingle();
    }

    @Override
    public Optional<SecurityPrincipal> findByExternalIdentity(String tenantId, String issuer, String subject) {
        IdnSecurityPrincipal p = IDN_SECURITY_PRINCIPAL;
        IdnIdentityLink link = IDN_IDENTITY_LINK;
        return principalSelect()
                .join(link)
                .on(link.TENANT_ID.eq(p.TENANT_ID))
                .and(link.PRINCIPAL_ID.eq(p.PRINCIPAL_ID))
                .where(link.TENANT_ID.eq(tenantId))
                .and(link.ISSUER.eq(issuer))
                .and(link.SUBJECT_VALUE.eq(subject))
                .fetchOptional(this::mapPrincipal);
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
        IdnSecurityPrincipal p = IDN_SECURITY_PRINCIPAL;
        var query = principalSelect()
                .where(p.TENANT_ID.eq(tenantId))
                .and(p.PRINCIPAL_ID.eq(principalId));
        if (forUpdate) {
            return query.forUpdate().of(p).fetchOptional(this::mapPrincipal);
        }
        return query.fetchOptional(this::mapPrincipal);
    }

    private SelectOnConditionStep<Record13<UUID, String, String, String, Long, Instant, Instant, Instant,
            String, String, String, String, Long>> principalSelect() {
        IdnSecurityPrincipal p = IDN_SECURITY_PRINCIPAL;
        IdnPersonProfile profile = IDN_PERSON_PROFILE;
        return dsl.select(p.PRINCIPAL_ID, p.TENANT_ID, p.PRINCIPAL_TYPE, p.PRINCIPAL_STATUS,
                        p.AGGREGATE_VERSION, p.CREATED_AT, p.UPDATED_AT,
                        p.DISABLED_AT, p.DISABLED_BY, p.DISABLED_REASON,
                        profile.DISPLAY_NAME, profile.EMPLOYEE_NUMBER, profile.PROFILE_VERSION)
                .from(p)
                .join(profile)
                .on(profile.TENANT_ID.eq(p.TENANT_ID))
                .and(profile.PRINCIPAL_ID.eq(p.PRINCIPAL_ID));
    }

    @Override
    public void insertPrincipal(SecurityPrincipal principal, String actorId) {
        IdnSecurityPrincipal p = IDN_SECURITY_PRINCIPAL;
        dsl.insertInto(p)
                .set(p.PRINCIPAL_ID, principal.id())
                .set(p.TENANT_ID, principal.tenantId())
                .set(p.PRINCIPAL_TYPE, principal.type().name())
                .set(p.PRINCIPAL_STATUS, principal.status().name())
                .set(p.AGGREGATE_VERSION, principal.version())
                .set(p.CREATED_AT, principal.createdAt())
                .set(p.UPDATED_AT, principal.updatedAt())
                .execute();
        IdnPersonProfile profile = IDN_PERSON_PROFILE;
        dsl.insertInto(profile)
                .set(profile.PRINCIPAL_ID, principal.id())
                .set(profile.TENANT_ID, principal.tenantId())
                .set(profile.DISPLAY_NAME, principal.displayName())
                .set(profile.EMPLOYEE_NUMBER, principal.employeeNumber())
                .set(profile.PROFILE_VERSION, 1L)
                .set(profile.CREATED_AT, principal.createdAt())
                .set(profile.UPDATED_AT, principal.createdAt())
                .set(profile.UPDATED_BY, actorId)
                .execute();
    }

    @Override
    public void insertIdentityLink(
            UUID linkId, String tenantId, UUID principalId, String issuer, String subject,
            String clientId, String actorId, Instant now
    ) {
        IdnIdentityLink link = IDN_IDENTITY_LINK;
        int inserted = dsl.insertInto(link)
                .set(link.IDENTITY_LINK_ID, linkId)
                .set(link.TENANT_ID, tenantId)
                .set(link.PRINCIPAL_ID, principalId)
                .set(link.ISSUER, issuer)
                .set(link.SUBJECT_VALUE, subject)
                .set(link.CLIENT_ID, clientId)
                .set(link.LINKED_BY, actorId)
                .set(link.LINKED_AT, now)
                .onConflict(link.TENANT_ID, link.ISSUER, link.SUBJECT_VALUE)
                .doNothing()
                .execute();
        if (inserted != 1) {
            throw new BusinessProblem(ProblemCode.IDENTITY_LINK_CONFLICT, "外部身份已经绑定");
        }
    }

    @Override
    public boolean advanceLifecycle(
            String tenantId, UUID principalId, long expectedVersion, String status,
            String actorId, String reason, Instant now
    ) {
        IdnSecurityPrincipal p = IDN_SECURITY_PRINCIPAL;
        // disabled_* 三列只在停用动作写入，其余生命周期迁移（如启用）必须清空。
        boolean disabling = "DISABLED".equals(status);
        return dsl.update(p)
                .set(p.PRINCIPAL_STATUS, status)
                .set(p.AGGREGATE_VERSION, p.AGGREGATE_VERSION.plus(1))
                .set(p.UPDATED_AT, now)
                .set(p.DISABLED_AT, disabling ? now : null)
                .set(p.DISABLED_BY, disabling ? actorId : null)
                .set(p.DISABLED_REASON, disabling ? reason : null)
                .where(p.TENANT_ID.eq(tenantId))
                .and(p.PRINCIPAL_ID.eq(principalId))
                .and(p.AGGREGATE_VERSION.eq(expectedVersion))
                .execute() == 1;
    }

    @Override
    public boolean advanceVersion(String tenantId, UUID principalId, long expectedVersion, Instant now) {
        IdnSecurityPrincipal p = IDN_SECURITY_PRINCIPAL;
        return dsl.update(p)
                .set(p.AGGREGATE_VERSION, p.AGGREGATE_VERSION.plus(1))
                .set(p.UPDATED_AT, now)
                .where(p.TENANT_ID.eq(tenantId))
                .and(p.PRINCIPAL_ID.eq(principalId))
                .and(p.AGGREGATE_VERSION.eq(expectedVersion))
                .execute() == 1;
    }

    @Override
    public boolean updateProfile(
            String tenantId, UUID principalId, long expectedPrincipalVersion,
            String displayName, String employeeNumber, String actorId, Instant now
    ) {
        if (!advanceVersion(tenantId, principalId, expectedPrincipalVersion, now)) return false;
        IdnPersonProfile profile = IDN_PERSON_PROFILE;
        try {
            int updated = dsl.update(profile)
                    .set(profile.DISPLAY_NAME, displayName)
                    .set(profile.EMPLOYEE_NUMBER, employeeNumber)
                    .set(profile.PROFILE_VERSION, profile.PROFILE_VERSION.plus(1))
                    .set(profile.UPDATED_AT, now)
                    .set(profile.UPDATED_BY, actorId)
                    .where(profile.TENANT_ID.eq(tenantId))
                    .and(profile.PRINCIPAL_ID.eq(principalId))
                    .execute();
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
        IdnPrincipalPersona persona = IDN_PRINCIPAL_PERSONA;
        int inserted = dsl.insertInto(persona)
                .set(persona.PERSONA_ID, personaId)
                .set(persona.TENANT_ID, tenantId)
                .set(persona.PRINCIPAL_ID, principalId)
                .set(persona.PERSONA_TYPE, personaType)
                .set(persona.PERSONA_STATUS, "ACTIVE")
                .set(persona.VALID_FROM, validFrom)
                .set(persona.VALID_TO, validTo)
                .set(persona.PERSONA_VERSION, 1L)
                .set(persona.CREATED_BY, actorId)
                .set(persona.CREATED_AT, now)
                .onConflict(persona.TENANT_ID, persona.PRINCIPAL_ID, persona.PERSONA_TYPE)
                .doNothing()
                .execute();
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
        IdnPrincipalLifecycleEvent event = IDN_PRINCIPAL_LIFECYCLE_EVENT;
        dsl.insertInto(event)
                .set(event.LIFECYCLE_EVENT_ID, eventId)
                .set(event.TENANT_ID, tenantId)
                .set(event.PRINCIPAL_ID, principalId)
                .set(event.EVENT_TYPE, eventType)
                .set(event.PRINCIPAL_VERSION, principalVersion)
                .set(event.REASON, reason)
                .set(event.ACTOR_ID, actorId)
                .set(event.REQUEST_DIGEST, requestDigest)
                .set(event.CORRELATION_ID, correlationId)
                .set(event.OCCURRED_AT, now)
                .execute();
    }

    @Override
    public List<IdentityLinkView> findIdentityLinks(String tenantId, UUID principalId) {
        IdnIdentityLink link = IDN_IDENTITY_LINK;
        return dsl.select(link.IDENTITY_LINK_ID, link.ISSUER, link.SUBJECT_VALUE, link.CLIENT_ID, link.LINKED_AT)
                .from(link)
                .where(link.TENANT_ID.eq(tenantId))
                .and(link.PRINCIPAL_ID.eq(principalId))
                .orderBy(link.LINKED_AT, link.IDENTITY_LINK_ID)
                .fetch(record -> new IdentityLinkView(
                        record.value1(), record.value2(), record.value3(), record.value4(), record.value5()));
    }

    @Override
    public List<PrincipalPersonaView> findPersonas(String tenantId, UUID principalId) {
        IdnPrincipalPersona persona = IDN_PRINCIPAL_PERSONA;
        return personaSelect()
                .where(persona.TENANT_ID.eq(tenantId))
                .and(persona.PRINCIPAL_ID.eq(principalId))
                .orderBy(persona.PERSONA_TYPE, persona.PERSONA_ID)
                .fetch(JooqIdentityDirectoryRepository::mapPersona);
    }

    @Override
    public Optional<PrincipalPersonaView> findPersona(String tenantId, UUID personaId) {
        IdnPrincipalPersona persona = IDN_PRINCIPAL_PERSONA;
        return personaSelect()
                .where(persona.TENANT_ID.eq(tenantId))
                .and(persona.PERSONA_ID.eq(personaId))
                .fetchOptional(JooqIdentityDirectoryRepository::mapPersona);
    }

    private SelectJoinStep<Record6<UUID, String, String, Instant, Instant, Long>> personaSelect() {
        IdnPrincipalPersona persona = IDN_PRINCIPAL_PERSONA;
        return dsl.select(persona.PERSONA_ID, persona.PERSONA_TYPE, persona.PERSONA_STATUS,
                        persona.VALID_FROM, persona.VALID_TO, persona.PERSONA_VERSION)
                .from(persona);
    }

    private SecurityPrincipal mapPrincipal(Record record) {
        IdnSecurityPrincipal p = IDN_SECURITY_PRINCIPAL;
        IdnPersonProfile profile = IDN_PERSON_PROFILE;
        return new SecurityPrincipal(
                record.get(p.PRINCIPAL_ID), record.get(p.TENANT_ID),
                SecurityPrincipal.Type.valueOf(record.get(p.PRINCIPAL_TYPE)),
                SecurityPrincipal.Status.valueOf(record.get(p.PRINCIPAL_STATUS)),
                record.get(p.AGGREGATE_VERSION),
                record.get(p.CREATED_AT), record.get(p.UPDATED_AT),
                record.get(p.DISABLED_AT), record.get(p.DISABLED_BY), record.get(p.DISABLED_REASON),
                record.get(profile.DISPLAY_NAME), record.get(profile.EMPLOYEE_NUMBER),
                record.get(profile.PROFILE_VERSION));
    }

    private static PrincipalPersonaView mapPersona(Record6<UUID, String, String, Instant, Instant, Long> record) {
        return new PrincipalPersonaView(record.value1(), record.value2(), record.value3(),
                record.value4(), record.value5(), record.value6());
    }
}
