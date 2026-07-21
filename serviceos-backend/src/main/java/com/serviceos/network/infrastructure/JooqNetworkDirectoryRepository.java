package com.serviceos.network.infrastructure;

import com.serviceos.jooq.generated.tables.NetClearanceWorkItem;
import com.serviceos.jooq.generated.tables.NetNetworkMembership;
import com.serviceos.jooq.generated.tables.NetNetworkTechnicianMembership;
import com.serviceos.jooq.generated.tables.NetPartnerOrganization;
import com.serviceos.jooq.generated.tables.NetServiceNetwork;
import com.serviceos.jooq.generated.tables.NetTechnicianProfile;
import com.serviceos.jooq.generated.tables.NetTechnicianQualification;
import com.serviceos.network.api.ClearanceWorkItemView;
import com.serviceos.network.application.NetworkDirectoryRepository;
import com.serviceos.network.domain.NetworkMembership;
import com.serviceos.network.domain.NetworkTechnicianMembership;
import com.serviceos.network.domain.PartnerOrganization;
import com.serviceos.network.domain.ServiceNetwork;
import com.serviceos.network.domain.TechnicianProfile;
import com.serviceos.network.domain.TechnicianQualification;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.NetClearanceWorkItem.NET_CLEARANCE_WORK_ITEM;
import static com.serviceos.jooq.generated.tables.NetDirectoryEvent.NET_DIRECTORY_EVENT;
import static com.serviceos.jooq.generated.tables.NetNetworkMembership.NET_NETWORK_MEMBERSHIP;
import static com.serviceos.jooq.generated.tables.NetNetworkTechnicianMembership.NET_NETWORK_TECHNICIAN_MEMBERSHIP;
import static com.serviceos.jooq.generated.tables.NetPartnerOrganization.NET_PARTNER_ORGANIZATION;
import static com.serviceos.jooq.generated.tables.NetServiceNetwork.NET_SERVICE_NETWORK;
import static com.serviceos.jooq.generated.tables.NetTechnicianProfile.NET_TECHNICIAN_PROFILE;
import static com.serviceos.jooq.generated.tables.NetTechnicianQualification.NET_TECHNICIAN_QUALIFICATION;

/**
 * 网点目录 jOOQ 适配器：成员/师傅唯一约束与清退待办依赖 PostgreSQL 精确语义。
 * 唯一键冲突由中央异常翻译转为 Spring DuplicateKeyException，在此翻译为业务问题。
 */
@Repository
final class JooqNetworkDirectoryRepository implements NetworkDirectoryRepository {
    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    JooqNetworkDirectoryRepository(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<PartnerOrganization> findPartner(String tenantId, UUID partnerOrganizationId) {
        NetPartnerOrganization p = NET_PARTNER_ORGANIZATION;
        return dsl.select(
                        p.PARTNER_ORGANIZATION_ID, p.TENANT_ID, p.PARTNER_CODE, p.PARTNER_NAME,
                        p.PARTNER_STATUS, p.AGGREGATE_VERSION, p.CREATED_AT, p.UPDATED_AT)
                .from(p)
                .where(p.TENANT_ID.eq(tenantId))
                .and(p.PARTNER_ORGANIZATION_ID.eq(partnerOrganizationId))
                .fetchOptional(JooqNetworkDirectoryRepository::mapPartner);
    }

    @Override
    public List<PartnerOrganization> listPartners(String tenantId) {
        NetPartnerOrganization p = NET_PARTNER_ORGANIZATION;
        return dsl.select(
                        p.PARTNER_ORGANIZATION_ID, p.TENANT_ID, p.PARTNER_CODE, p.PARTNER_NAME,
                        p.PARTNER_STATUS, p.AGGREGATE_VERSION, p.CREATED_AT, p.UPDATED_AT)
                .from(p)
                .where(p.TENANT_ID.eq(tenantId))
                .orderBy(p.PARTNER_CODE)
                .fetch(JooqNetworkDirectoryRepository::mapPartner);
    }

    @Override
    public void insertPartner(PartnerOrganization partner) {
        NetPartnerOrganization p = NET_PARTNER_ORGANIZATION;
        try {
            dsl.insertInto(p)
                    .set(p.PARTNER_ORGANIZATION_ID, partner.id())
                    .set(p.TENANT_ID, partner.tenantId())
                    .set(p.PARTNER_CODE, partner.code())
                    .set(p.PARTNER_NAME, partner.name())
                    .set(p.PARTNER_STATUS, partner.status().name())
                    .set(p.AGGREGATE_VERSION, partner.version())
                    .set(p.CREATED_AT, partner.createdAt())
                    .set(p.UPDATED_AT, partner.updatedAt())
                    .execute();
        } catch (DuplicateKeyException exception) {
            throw new BusinessProblem(ProblemCode.NETWORK_AUTHORITY_CONFLICT, "合作组织编码已存在");
        }
    }

    @Override
    public Optional<ServiceNetwork> findNetwork(String tenantId, UUID serviceNetworkId) {
        return networkQuery(tenantId, serviceNetworkId, false);
    }

    @Override
    public Optional<ServiceNetwork> findNetworkForUpdate(String tenantId, UUID serviceNetworkId) {
        return networkQuery(tenantId, serviceNetworkId, true);
    }

    private Optional<ServiceNetwork> networkQuery(String tenantId, UUID serviceNetworkId, boolean forUpdate) {
        NetServiceNetwork n = NET_SERVICE_NETWORK;
        var query = dsl.select(
                        n.SERVICE_NETWORK_ID, n.TENANT_ID, n.PARTNER_ORGANIZATION_ID,
                        n.NETWORK_CODE, n.NETWORK_NAME, n.NETWORK_STATUS, n.AGGREGATE_VERSION,
                        n.CREATED_AT, n.UPDATED_AT, n.DEACTIVATED_AT, n.DEACTIVATED_BY, n.DEACTIVATE_REASON)
                .from(n)
                .where(n.TENANT_ID.eq(tenantId))
                .and(n.SERVICE_NETWORK_ID.eq(serviceNetworkId));
        if (forUpdate) {
            return query.forUpdate().fetchOptional(JooqNetworkDirectoryRepository::mapNetwork);
        }
        return query.fetchOptional(JooqNetworkDirectoryRepository::mapNetwork);
    }

    @Override
    public List<ServiceNetwork> listNetworks(String tenantId, UUID partnerOrganizationId) {
        NetServiceNetwork n = NET_SERVICE_NETWORK;
        Condition condition = n.TENANT_ID.eq(tenantId);
        if (partnerOrganizationId != null) {
            condition = condition.and(n.PARTNER_ORGANIZATION_ID.eq(partnerOrganizationId));
        }
        return dsl.select(
                        n.SERVICE_NETWORK_ID, n.TENANT_ID, n.PARTNER_ORGANIZATION_ID,
                        n.NETWORK_CODE, n.NETWORK_NAME, n.NETWORK_STATUS, n.AGGREGATE_VERSION,
                        n.CREATED_AT, n.UPDATED_AT, n.DEACTIVATED_AT, n.DEACTIVATED_BY, n.DEACTIVATE_REASON)
                .from(n)
                .where(condition)
                .orderBy(n.NETWORK_CODE)
                .fetch(JooqNetworkDirectoryRepository::mapNetwork);
    }

    @Override
    public void insertNetwork(ServiceNetwork network) {
        NetServiceNetwork n = NET_SERVICE_NETWORK;
        try {
            dsl.insertInto(n)
                    .set(n.SERVICE_NETWORK_ID, network.id())
                    .set(n.TENANT_ID, network.tenantId())
                    .set(n.PARTNER_ORGANIZATION_ID, network.partnerOrganizationId())
                    .set(n.NETWORK_CODE, network.networkCode())
                    .set(n.NETWORK_NAME, network.networkName())
                    .set(n.NETWORK_STATUS, network.status().name())
                    .set(n.AGGREGATE_VERSION, network.version())
                    .set(n.CREATED_AT, network.createdAt())
                    .set(n.UPDATED_AT, network.updatedAt())
                    .execute();
        } catch (DuplicateKeyException exception) {
            throw new BusinessProblem(ProblemCode.NETWORK_AUTHORITY_CONFLICT, "网点编码已存在");
        }
    }

    @Override
    public boolean deactivateNetwork(String tenantId, UUID serviceNetworkId, long expectedVersion,
            String reason, String actorId, Instant now) {
        NetServiceNetwork n = NET_SERVICE_NETWORK;
        // 乐观并发：版本与原状态条件不匹配即影响 0 行，失败关闭。
        return dsl.update(n)
                .set(n.NETWORK_STATUS, "DEACTIVATED")
                .set(n.AGGREGATE_VERSION, n.AGGREGATE_VERSION.plus(1))
                .set(n.UPDATED_AT, now)
                .set(n.DEACTIVATED_AT, now)
                .set(n.DEACTIVATED_BY, actorId)
                .set(n.DEACTIVATE_REASON, reason)
                .where(n.TENANT_ID.eq(tenantId))
                .and(n.SERVICE_NETWORK_ID.eq(serviceNetworkId))
                .and(n.AGGREGATE_VERSION.eq(expectedVersion))
                .and(n.NETWORK_STATUS.eq("ACTIVE"))
                .execute() == 1;
    }

    @Override
    public Optional<NetworkMembership> findMembership(String tenantId, UUID membershipId) {
        return membershipQuery(tenantId, membershipId, false);
    }

    @Override
    public Optional<NetworkMembership> findMembershipForUpdate(String tenantId, UUID membershipId) {
        return membershipQuery(tenantId, membershipId, true);
    }

    private Optional<NetworkMembership> membershipQuery(String tenantId, UUID membershipId, boolean forUpdate) {
        NetNetworkMembership m = NET_NETWORK_MEMBERSHIP;
        var query = dsl.select(
                        m.MEMBERSHIP_ID, m.TENANT_ID, m.SERVICE_NETWORK_ID, m.PRINCIPAL_ID,
                        m.MEMBERSHIP_ROLE, m.MEMBERSHIP_STATUS, m.VALID_FROM, m.VALID_TO,
                        m.INVITED_BY, m.CREATED_AT, m.TERMINATED_BY, m.TERMINATED_AT,
                        m.TERMINATE_REASON, m.AGGREGATE_VERSION)
                .from(m)
                .where(m.TENANT_ID.eq(tenantId))
                .and(m.MEMBERSHIP_ID.eq(membershipId));
        if (forUpdate) {
            return query.forUpdate().fetchOptional(JooqNetworkDirectoryRepository::mapMembership);
        }
        return query.fetchOptional(JooqNetworkDirectoryRepository::mapMembership);
    }

    @Override
    public List<NetworkMembership> listMemberships(String tenantId, UUID serviceNetworkId, UUID principalId) {
        NetNetworkMembership m = NET_NETWORK_MEMBERSHIP;
        List<Condition> conditions = new ArrayList<>();
        conditions.add(m.TENANT_ID.eq(tenantId));
        if (serviceNetworkId != null) {
            conditions.add(m.SERVICE_NETWORK_ID.eq(serviceNetworkId));
        }
        if (principalId != null) {
            conditions.add(m.PRINCIPAL_ID.eq(principalId));
        }
        return dsl.select(
                        m.MEMBERSHIP_ID, m.TENANT_ID, m.SERVICE_NETWORK_ID, m.PRINCIPAL_ID,
                        m.MEMBERSHIP_ROLE, m.MEMBERSHIP_STATUS, m.VALID_FROM, m.VALID_TO,
                        m.INVITED_BY, m.CREATED_AT, m.TERMINATED_BY, m.TERMINATED_AT,
                        m.TERMINATE_REASON, m.AGGREGATE_VERSION)
                .from(m)
                .where(conditions)
                .orderBy(m.CREATED_AT)
                .fetch(JooqNetworkDirectoryRepository::mapMembership);
    }

    @Override
    public void insertMembership(NetworkMembership membership) {
        NetNetworkMembership m = NET_NETWORK_MEMBERSHIP;
        try {
            dsl.insertInto(m)
                    .set(m.MEMBERSHIP_ID, membership.id())
                    .set(m.TENANT_ID, membership.tenantId())
                    .set(m.SERVICE_NETWORK_ID, membership.serviceNetworkId())
                    .set(m.PRINCIPAL_ID, membership.principalId())
                    .set(m.MEMBERSHIP_ROLE, membership.role().name())
                    .set(m.MEMBERSHIP_STATUS, membership.status().name())
                    .set(m.VALID_FROM, membership.validFrom())
                    .set(m.INVITED_BY, membership.invitedBy())
                    .set(m.CREATED_AT, membership.createdAt())
                    .set(m.AGGREGATE_VERSION, membership.version())
                    .execute();
        } catch (DuplicateKeyException exception) {
            throw new BusinessProblem(ProblemCode.NETWORK_MEMBERSHIP_CONFLICT, "该主体在此网点已有有效成员关系");
        }
    }

    @Override
    public boolean terminateMembership(String tenantId, UUID membershipId, long expectedVersion,
            String reason, String actorId, Instant terminatedAt) {
        NetNetworkMembership m = NET_NETWORK_MEMBERSHIP;
        return dsl.update(m)
                .set(m.MEMBERSHIP_STATUS, "TERMINATED")
                .set(m.AGGREGATE_VERSION, m.AGGREGATE_VERSION.plus(1))
                .set(m.VALID_TO, terminatedAt)
                .set(m.TERMINATED_BY, actorId)
                .set(m.TERMINATED_AT, terminatedAt)
                .set(m.TERMINATE_REASON, reason)
                .where(m.TENANT_ID.eq(tenantId))
                .and(m.MEMBERSHIP_ID.eq(membershipId))
                .and(m.AGGREGATE_VERSION.eq(expectedVersion))
                .and(m.MEMBERSHIP_STATUS.eq("ACTIVE"))
                .execute() == 1;
    }

    @Override
    public Optional<TechnicianProfile> findTechnicianProfile(String tenantId, UUID profileId) {
        return technicianQuery(tenantId, profileId, false);
    }

    @Override
    public Optional<TechnicianProfile> findTechnicianProfileForUpdate(String tenantId, UUID profileId) {
        return technicianQuery(tenantId, profileId, true);
    }

    private Optional<TechnicianProfile> technicianQuery(String tenantId, UUID profileId, boolean forUpdate) {
        NetTechnicianProfile t = NET_TECHNICIAN_PROFILE;
        var query = dsl.select(
                        t.TECHNICIAN_PROFILE_ID, t.TENANT_ID, t.PRINCIPAL_ID, t.DISPLAY_NAME,
                        t.PROFILE_STATUS, t.SUPPORTED_CLIENT_KINDS, t.AGGREGATE_VERSION,
                        t.CREATED_AT, t.UPDATED_AT, t.DISABLED_AT, t.DISABLED_BY, t.DISABLED_REASON)
                .from(t)
                .where(t.TENANT_ID.eq(tenantId))
                .and(t.TECHNICIAN_PROFILE_ID.eq(profileId));
        if (forUpdate) {
            return query.forUpdate().fetchOptional(this::mapTechnician);
        }
        return query.fetchOptional(this::mapTechnician);
    }

    @Override
    public Optional<TechnicianProfile> findTechnicianProfileByPrincipal(String tenantId, UUID principalId) {
        NetTechnicianProfile t = NET_TECHNICIAN_PROFILE;
        return dsl.select(
                        t.TECHNICIAN_PROFILE_ID, t.TENANT_ID, t.PRINCIPAL_ID, t.DISPLAY_NAME,
                        t.PROFILE_STATUS, t.SUPPORTED_CLIENT_KINDS, t.AGGREGATE_VERSION,
                        t.CREATED_AT, t.UPDATED_AT, t.DISABLED_AT, t.DISABLED_BY, t.DISABLED_REASON)
                .from(t)
                .where(t.TENANT_ID.eq(tenantId))
                .and(t.PRINCIPAL_ID.eq(principalId))
                .fetchOptional(this::mapTechnician);
    }

    @Override
    public List<TechnicianProfile> listTechnicianProfiles(String tenantId) {
        NetTechnicianProfile t = NET_TECHNICIAN_PROFILE;
        return dsl.select(
                        t.TECHNICIAN_PROFILE_ID, t.TENANT_ID, t.PRINCIPAL_ID, t.DISPLAY_NAME,
                        t.PROFILE_STATUS, t.SUPPORTED_CLIENT_KINDS, t.AGGREGATE_VERSION,
                        t.CREATED_AT, t.UPDATED_AT, t.DISABLED_AT, t.DISABLED_BY, t.DISABLED_REASON)
                .from(t)
                .where(t.TENANT_ID.eq(tenantId))
                .orderBy(t.DISPLAY_NAME)
                .fetch(this::mapTechnician);
    }

    @Override
    public void insertTechnicianProfile(TechnicianProfile profile) {
        NetTechnicianProfile t = NET_TECHNICIAN_PROFILE;
        try {
            dsl.insertInto(t)
                    .set(t.TECHNICIAN_PROFILE_ID, profile.id())
                    .set(t.TENANT_ID, profile.tenantId())
                    .set(t.PRINCIPAL_ID, profile.principalId())
                    .set(t.DISPLAY_NAME, profile.displayName())
                    .set(t.PROFILE_STATUS, profile.status().name())
                    .set(t.SUPPORTED_CLIENT_KINDS, writeKindsJson(profile.supportedClientKinds()))
                    .set(t.AGGREGATE_VERSION, profile.version())
                    .set(t.CREATED_AT, profile.createdAt())
                    .set(t.UPDATED_AT, profile.updatedAt())
                    .execute();
        } catch (DuplicateKeyException exception) {
            throw new BusinessProblem(ProblemCode.NETWORK_TECHNICIAN_CONFLICT, "该主体已有师傅档案");
        }
    }

    @Override
    public boolean declareTechnicianSupportedClientKinds(
            String tenantId,
            UUID profileId,
            long expectedVersion,
            List<String> supportedClientKinds,
            Instant now
    ) {
        NetTechnicianProfile t = NET_TECHNICIAN_PROFILE;
        // jsonb 列由生成物 JsonbStringConverter 绑定，直接写字符串，不再需要 CAST。
        return dsl.update(t)
                .set(t.SUPPORTED_CLIENT_KINDS, writeKindsJson(supportedClientKinds))
                .set(t.AGGREGATE_VERSION, t.AGGREGATE_VERSION.plus(1))
                .set(t.UPDATED_AT, now)
                .where(t.TENANT_ID.eq(tenantId))
                .and(t.TECHNICIAN_PROFILE_ID.eq(profileId))
                .and(t.AGGREGATE_VERSION.eq(expectedVersion))
                .execute() == 1;
    }

    @Override
    public boolean disableTechnicianProfile(String tenantId, UUID profileId, long expectedVersion,
            String reason, String actorId, Instant now) {
        NetTechnicianProfile t = NET_TECHNICIAN_PROFILE;
        return dsl.update(t)
                .set(t.PROFILE_STATUS, "DISABLED")
                .set(t.AGGREGATE_VERSION, t.AGGREGATE_VERSION.plus(1))
                .set(t.UPDATED_AT, now)
                .set(t.DISABLED_AT, now)
                .set(t.DISABLED_BY, actorId)
                .set(t.DISABLED_REASON, reason)
                .where(t.TENANT_ID.eq(tenantId))
                .and(t.TECHNICIAN_PROFILE_ID.eq(profileId))
                .and(t.AGGREGATE_VERSION.eq(expectedVersion))
                .and(t.PROFILE_STATUS.eq("ACTIVE"))
                .execute() == 1;
    }

    @Override
    public boolean enableTechnicianProfile(String tenantId, UUID profileId, long expectedVersion, Instant now) {
        NetTechnicianProfile t = NET_TECHNICIAN_PROFILE;
        return dsl.update(t)
                .set(t.PROFILE_STATUS, "ACTIVE")
                .set(t.AGGREGATE_VERSION, t.AGGREGATE_VERSION.plus(1))
                .set(t.UPDATED_AT, now)
                .setNull(t.DISABLED_AT)
                .setNull(t.DISABLED_BY)
                .setNull(t.DISABLED_REASON)
                .where(t.TENANT_ID.eq(tenantId))
                .and(t.TECHNICIAN_PROFILE_ID.eq(profileId))
                .and(t.AGGREGATE_VERSION.eq(expectedVersion))
                .and(t.PROFILE_STATUS.eq("DISABLED"))
                .execute() == 1;
    }

    @Override
    public Optional<NetworkTechnicianMembership> findTechnicianMembership(String tenantId, UUID membershipId) {
        return techMembershipQuery(tenantId, membershipId, false);
    }

    @Override
    public Optional<NetworkTechnicianMembership> findTechnicianMembershipForUpdate(String tenantId, UUID membershipId) {
        return techMembershipQuery(tenantId, membershipId, true);
    }

    private Optional<NetworkTechnicianMembership> techMembershipQuery(
            String tenantId, UUID membershipId, boolean forUpdate
    ) {
        NetNetworkTechnicianMembership tm = NET_NETWORK_TECHNICIAN_MEMBERSHIP;
        var query = dsl.select(
                        tm.MEMBERSHIP_ID, tm.TENANT_ID, tm.SERVICE_NETWORK_ID, tm.TECHNICIAN_PROFILE_ID,
                        tm.MEMBERSHIP_STATUS, tm.VALID_FROM, tm.VALID_TO, tm.CREATED_BY, tm.CREATED_AT,
                        tm.TERMINATED_BY, tm.TERMINATED_AT, tm.TERMINATE_REASON, tm.AGGREGATE_VERSION)
                .from(tm)
                .where(tm.TENANT_ID.eq(tenantId))
                .and(tm.MEMBERSHIP_ID.eq(membershipId));
        if (forUpdate) {
            return query.forUpdate().fetchOptional(JooqNetworkDirectoryRepository::mapTechMembership);
        }
        return query.fetchOptional(JooqNetworkDirectoryRepository::mapTechMembership);
    }

    @Override
    public List<NetworkTechnicianMembership> listTechnicianMemberships(
            String tenantId, UUID serviceNetworkId, UUID technicianProfileId
    ) {
        NetNetworkTechnicianMembership tm = NET_NETWORK_TECHNICIAN_MEMBERSHIP;
        List<Condition> conditions = new ArrayList<>();
        conditions.add(tm.TENANT_ID.eq(tenantId));
        if (serviceNetworkId != null) {
            conditions.add(tm.SERVICE_NETWORK_ID.eq(serviceNetworkId));
        }
        if (technicianProfileId != null) {
            conditions.add(tm.TECHNICIAN_PROFILE_ID.eq(technicianProfileId));
        }
        return dsl.select(
                        tm.MEMBERSHIP_ID, tm.TENANT_ID, tm.SERVICE_NETWORK_ID, tm.TECHNICIAN_PROFILE_ID,
                        tm.MEMBERSHIP_STATUS, tm.VALID_FROM, tm.VALID_TO, tm.CREATED_BY, tm.CREATED_AT,
                        tm.TERMINATED_BY, tm.TERMINATED_AT, tm.TERMINATE_REASON, tm.AGGREGATE_VERSION)
                .from(tm)
                .where(conditions)
                .orderBy(tm.CREATED_AT)
                .fetch(JooqNetworkDirectoryRepository::mapTechMembership);
    }

    @Override
    public Optional<NetworkTechnicianMembership> findActiveTechnicianMembership(
            String tenantId, UUID serviceNetworkId, UUID technicianProfileId, Instant at
    ) {
        NetNetworkTechnicianMembership tm = NET_NETWORK_TECHNICIAN_MEMBERSHIP;
        return dsl.select(
                        tm.MEMBERSHIP_ID, tm.TENANT_ID, tm.SERVICE_NETWORK_ID, tm.TECHNICIAN_PROFILE_ID,
                        tm.MEMBERSHIP_STATUS, tm.VALID_FROM, tm.VALID_TO, tm.CREATED_BY, tm.CREATED_AT,
                        tm.TERMINATED_BY, tm.TERMINATED_AT, tm.TERMINATE_REASON, tm.AGGREGATE_VERSION)
                .from(tm)
                .where(tm.TENANT_ID.eq(tenantId))
                .and(tm.SERVICE_NETWORK_ID.eq(serviceNetworkId))
                .and(tm.TECHNICIAN_PROFILE_ID.eq(technicianProfileId))
                .and(tm.MEMBERSHIP_STATUS.eq("ACTIVE"))
                .and(tm.VALID_FROM.le(at))
                .and(tm.VALID_TO.isNull().or(tm.VALID_TO.gt(at)))
                .fetchOptional(JooqNetworkDirectoryRepository::mapTechMembership);
    }

    @Override
    public void insertTechnicianMembership(NetworkTechnicianMembership membership) {
        NetNetworkTechnicianMembership tm = NET_NETWORK_TECHNICIAN_MEMBERSHIP;
        try {
            dsl.insertInto(tm)
                    .set(tm.MEMBERSHIP_ID, membership.id())
                    .set(tm.TENANT_ID, membership.tenantId())
                    .set(tm.SERVICE_NETWORK_ID, membership.serviceNetworkId())
                    .set(tm.TECHNICIAN_PROFILE_ID, membership.technicianProfileId())
                    .set(tm.MEMBERSHIP_STATUS, membership.status().name())
                    .set(tm.VALID_FROM, membership.validFrom())
                    .set(tm.CREATED_BY, membership.createdBy())
                    .set(tm.CREATED_AT, membership.createdAt())
                    .set(tm.AGGREGATE_VERSION, membership.version())
                    .execute();
        } catch (DuplicateKeyException exception) {
            throw new BusinessProblem(ProblemCode.NETWORK_TECHNICIAN_CONFLICT, "该师傅在此网点已有有效服务关系");
        }
    }

    @Override
    public boolean terminateTechnicianMembership(String tenantId, UUID membershipId, long expectedVersion,
            String reason, String actorId, Instant terminatedAt) {
        NetNetworkTechnicianMembership tm = NET_NETWORK_TECHNICIAN_MEMBERSHIP;
        return dsl.update(tm)
                .set(tm.MEMBERSHIP_STATUS, "TERMINATED")
                .set(tm.AGGREGATE_VERSION, tm.AGGREGATE_VERSION.plus(1))
                .set(tm.VALID_TO, terminatedAt)
                .set(tm.TERMINATED_BY, actorId)
                .set(tm.TERMINATED_AT, terminatedAt)
                .set(tm.TERMINATE_REASON, reason)
                .where(tm.TENANT_ID.eq(tenantId))
                .and(tm.MEMBERSHIP_ID.eq(membershipId))
                .and(tm.AGGREGATE_VERSION.eq(expectedVersion))
                .and(tm.MEMBERSHIP_STATUS.eq("ACTIVE"))
                .execute() == 1;
    }

    @Override
    public Optional<TechnicianQualification> findQualification(String tenantId, UUID qualificationId) {
        return qualificationQuery(tenantId, qualificationId, false);
    }

    @Override
    public Optional<TechnicianQualification> findQualificationForUpdate(String tenantId, UUID qualificationId) {
        return qualificationQuery(tenantId, qualificationId, true);
    }

    private Optional<TechnicianQualification> qualificationQuery(
            String tenantId, UUID qualificationId, boolean forUpdate
    ) {
        NetTechnicianQualification q = NET_TECHNICIAN_QUALIFICATION;
        var query = dsl.select(
                        q.QUALIFICATION_ID, q.TENANT_ID, q.TECHNICIAN_PROFILE_ID, q.QUALIFICATION_CODE,
                        q.QUALIFICATION_STATUS, q.VALID_FROM, q.VALID_TO, q.SUBMITTED_BY, q.SUBMITTED_AT,
                        q.DECIDED_BY, q.DECIDED_AT, q.DECISION_REASON, q.AGGREGATE_VERSION)
                .from(q)
                .where(q.TENANT_ID.eq(tenantId))
                .and(q.QUALIFICATION_ID.eq(qualificationId));
        if (forUpdate) {
            return query.forUpdate().fetchOptional(JooqNetworkDirectoryRepository::mapQualification);
        }
        return query.fetchOptional(JooqNetworkDirectoryRepository::mapQualification);
    }

    @Override
    public List<TechnicianQualification> listQualifications(String tenantId, UUID technicianProfileId) {
        NetTechnicianQualification q = NET_TECHNICIAN_QUALIFICATION;
        return dsl.select(
                        q.QUALIFICATION_ID, q.TENANT_ID, q.TECHNICIAN_PROFILE_ID, q.QUALIFICATION_CODE,
                        q.QUALIFICATION_STATUS, q.VALID_FROM, q.VALID_TO, q.SUBMITTED_BY, q.SUBMITTED_AT,
                        q.DECIDED_BY, q.DECIDED_AT, q.DECISION_REASON, q.AGGREGATE_VERSION)
                .from(q)
                .where(q.TENANT_ID.eq(tenantId))
                .and(q.TECHNICIAN_PROFILE_ID.eq(technicianProfileId))
                .orderBy(q.SUBMITTED_AT.desc())
                .fetch(JooqNetworkDirectoryRepository::mapQualification);
    }

    @Override
    public boolean hasApprovedQualification(String tenantId, UUID technicianProfileId, Instant at) {
        NetTechnicianQualification q = NET_TECHNICIAN_QUALIFICATION;
        // 与原 count(*) > 0 等价：存在任一当前有效 APPROVED 资质即为真。
        return dsl.fetchExists(q,
                q.TENANT_ID.eq(tenantId),
                q.TECHNICIAN_PROFILE_ID.eq(technicianProfileId),
                q.QUALIFICATION_STATUS.eq("APPROVED"),
                q.VALID_FROM.le(at),
                q.VALID_TO.isNull().or(q.VALID_TO.gt(at)));
    }

    @Override
    public void insertQualification(TechnicianQualification qualification) {
        NetTechnicianQualification q = NET_TECHNICIAN_QUALIFICATION;
        dsl.insertInto(q)
                .set(q.QUALIFICATION_ID, qualification.id())
                .set(q.TENANT_ID, qualification.tenantId())
                .set(q.TECHNICIAN_PROFILE_ID, qualification.technicianProfileId())
                .set(q.QUALIFICATION_CODE, qualification.qualificationCode())
                .set(q.QUALIFICATION_STATUS, qualification.status().name())
                .set(q.VALID_FROM, qualification.validFrom())
                .set(q.VALID_TO, qualification.validTo())
                .set(q.SUBMITTED_BY, qualification.submittedBy())
                .set(q.SUBMITTED_AT, qualification.submittedAt())
                .set(q.AGGREGATE_VERSION, qualification.version())
                .execute();
    }

    @Override
    public boolean decideQualification(String tenantId, UUID qualificationId, long expectedVersion,
            String status, String reason, String actorId, Instant now) {
        NetTechnicianQualification q = NET_TECHNICIAN_QUALIFICATION;
        return dsl.update(q)
                .set(q.QUALIFICATION_STATUS, status)
                .set(q.AGGREGATE_VERSION, q.AGGREGATE_VERSION.plus(1))
                .set(q.DECIDED_BY, actorId)
                .set(q.DECIDED_AT, now)
                .set(q.DECISION_REASON, reason)
                .where(q.TENANT_ID.eq(tenantId))
                .and(q.QUALIFICATION_ID.eq(qualificationId))
                .and(q.AGGREGATE_VERSION.eq(expectedVersion))
                .and(q.QUALIFICATION_STATUS.eq("PENDING"))
                .execute() == 1;
    }

    @Override
    public void insertDirectoryEvent(UUID eventId, String tenantId, String eventType, String resourceType,
            UUID resourceId, long resourceVersion, String reason, String actorId,
            String requestDigest, String correlationId, Instant occurredAt) {
        var e = NET_DIRECTORY_EVENT;
        dsl.insertInto(e)
                .set(e.DIRECTORY_EVENT_ID, eventId)
                .set(e.TENANT_ID, tenantId)
                .set(e.EVENT_TYPE, eventType)
                .set(e.RESOURCE_TYPE, resourceType)
                .set(e.RESOURCE_ID, resourceId)
                .set(e.RESOURCE_VERSION, resourceVersion)
                .set(e.REASON, reason)
                .set(e.ACTOR_ID, actorId)
                .set(e.REQUEST_DIGEST, requestDigest)
                .set(e.CORRELATION_ID, correlationId)
                .set(e.OCCURRED_AT, occurredAt)
                .execute();
    }

    @Override
    public void insertClearanceWorkItem(UUID workItemId, String tenantId, String subjectType,
            UUID serviceNetworkId, UUID technicianProfileId, String reason,
            int openTasks, int openAppointments, int openVisits, int activeAssignments, int offlinePackages,
            String createdBy, String correlationId, Instant createdAt) {
        NetClearanceWorkItem w = NET_CLEARANCE_WORK_ITEM;
        dsl.insertInto(w)
                .set(w.WORK_ITEM_ID, workItemId)
                .set(w.TENANT_ID, tenantId)
                .set(w.SUBJECT_TYPE, subjectType)
                .set(w.SERVICE_NETWORK_ID, serviceNetworkId)
                .set(w.TECHNICIAN_PROFILE_ID, technicianProfileId)
                .set(w.WORK_ITEM_STATUS, "OPEN")
                .set(w.REASON, reason)
                .set(w.OPEN_TASK_COUNT, openTasks)
                .set(w.OPEN_APPOINTMENT_COUNT, openAppointments)
                .set(w.OPEN_VISIT_COUNT, openVisits)
                .set(w.ACTIVE_ASSIGNMENT_COUNT, activeAssignments)
                .set(w.OFFLINE_PACKAGE_COUNT, offlinePackages)
                .set(w.CREATED_BY, createdBy)
                .set(w.CREATED_AT, createdAt)
                .set(w.CORRELATION_ID, correlationId)
                .execute();
    }

    @Override
    public List<ClearanceWorkItemView> listOpenClearanceWorkItems(String tenantId) {
        NetClearanceWorkItem w = NET_CLEARANCE_WORK_ITEM;
        return dsl.select(
                        w.WORK_ITEM_ID, w.SUBJECT_TYPE, w.SERVICE_NETWORK_ID, w.TECHNICIAN_PROFILE_ID,
                        w.WORK_ITEM_STATUS, w.REASON, w.OPEN_TASK_COUNT, w.OPEN_APPOINTMENT_COUNT,
                        w.OPEN_VISIT_COUNT, w.ACTIVE_ASSIGNMENT_COUNT, w.OFFLINE_PACKAGE_COUNT,
                        w.CREATED_BY, w.CREATED_AT, w.CORRELATION_ID)
                .from(w)
                .where(w.TENANT_ID.eq(tenantId))
                .and(w.WORK_ITEM_STATUS.eq("OPEN"))
                .orderBy(w.CREATED_AT)
                .fetch(JooqNetworkDirectoryRepository::mapClearance);
    }

    private static PartnerOrganization mapPartner(Record record) {
        NetPartnerOrganization p = NET_PARTNER_ORGANIZATION;
        return new PartnerOrganization(
                record.get(p.PARTNER_ORGANIZATION_ID),
                record.get(p.TENANT_ID),
                record.get(p.PARTNER_CODE),
                record.get(p.PARTNER_NAME),
                PartnerOrganization.Status.valueOf(record.get(p.PARTNER_STATUS)),
                record.get(p.AGGREGATE_VERSION),
                record.get(p.CREATED_AT),
                record.get(p.UPDATED_AT));
    }

    private static ServiceNetwork mapNetwork(Record record) {
        NetServiceNetwork n = NET_SERVICE_NETWORK;
        return new ServiceNetwork(
                record.get(n.SERVICE_NETWORK_ID),
                record.get(n.TENANT_ID),
                record.get(n.PARTNER_ORGANIZATION_ID),
                record.get(n.NETWORK_CODE),
                record.get(n.NETWORK_NAME),
                ServiceNetwork.Status.valueOf(record.get(n.NETWORK_STATUS)),
                record.get(n.AGGREGATE_VERSION),
                record.get(n.CREATED_AT),
                record.get(n.UPDATED_AT),
                record.get(n.DEACTIVATED_AT),
                record.get(n.DEACTIVATED_BY),
                record.get(n.DEACTIVATE_REASON));
    }

    private static NetworkMembership mapMembership(Record record) {
        NetNetworkMembership m = NET_NETWORK_MEMBERSHIP;
        return new NetworkMembership(
                record.get(m.MEMBERSHIP_ID),
                record.get(m.TENANT_ID),
                record.get(m.SERVICE_NETWORK_ID),
                record.get(m.PRINCIPAL_ID),
                NetworkMembership.Role.valueOf(record.get(m.MEMBERSHIP_ROLE)),
                NetworkMembership.Status.valueOf(record.get(m.MEMBERSHIP_STATUS)),
                record.get(m.VALID_FROM),
                record.get(m.VALID_TO),
                record.get(m.INVITED_BY),
                record.get(m.CREATED_AT),
                record.get(m.TERMINATED_BY),
                record.get(m.TERMINATED_AT),
                record.get(m.TERMINATE_REASON),
                record.get(m.AGGREGATE_VERSION));
    }

    private TechnicianProfile mapTechnician(Record record) {
        NetTechnicianProfile t = NET_TECHNICIAN_PROFILE;
        return new TechnicianProfile(
                record.get(t.TECHNICIAN_PROFILE_ID),
                record.get(t.TENANT_ID),
                record.get(t.PRINCIPAL_ID),
                record.get(t.DISPLAY_NAME),
                TechnicianProfile.Status.valueOf(record.get(t.PROFILE_STATUS)),
                readKinds(record.get(t.SUPPORTED_CLIENT_KINDS)),
                record.get(t.AGGREGATE_VERSION),
                record.get(t.CREATED_AT),
                record.get(t.UPDATED_AT),
                record.get(t.DISABLED_AT),
                record.get(t.DISABLED_BY),
                record.get(t.DISABLED_REASON));
    }

    private List<String> readKinds(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return null;
        }
        try {
            List<String> kinds = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            return kinds == null || kinds.isEmpty() ? null : List.copyOf(kinds);
        } catch (JacksonException exception) {
            throw new IllegalStateException("supported_client_kinds 无法解码", exception);
        }
    }

    private String writeKindsJson(List<String> kinds) {
        if (kinds == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(kinds);
        } catch (JacksonException exception) {
            throw new IllegalStateException("supported_client_kinds 无法编码", exception);
        }
    }

    private static NetworkTechnicianMembership mapTechMembership(Record record) {
        NetNetworkTechnicianMembership tm = NET_NETWORK_TECHNICIAN_MEMBERSHIP;
        return new NetworkTechnicianMembership(
                record.get(tm.MEMBERSHIP_ID),
                record.get(tm.TENANT_ID),
                record.get(tm.SERVICE_NETWORK_ID),
                record.get(tm.TECHNICIAN_PROFILE_ID),
                NetworkTechnicianMembership.Status.valueOf(record.get(tm.MEMBERSHIP_STATUS)),
                record.get(tm.VALID_FROM),
                record.get(tm.VALID_TO),
                record.get(tm.CREATED_BY),
                record.get(tm.CREATED_AT),
                record.get(tm.TERMINATED_BY),
                record.get(tm.TERMINATED_AT),
                record.get(tm.TERMINATE_REASON),
                record.get(tm.AGGREGATE_VERSION));
    }

    private static TechnicianQualification mapQualification(Record record) {
        NetTechnicianQualification q = NET_TECHNICIAN_QUALIFICATION;
        return new TechnicianQualification(
                record.get(q.QUALIFICATION_ID),
                record.get(q.TENANT_ID),
                record.get(q.TECHNICIAN_PROFILE_ID),
                record.get(q.QUALIFICATION_CODE),
                TechnicianQualification.Status.valueOf(record.get(q.QUALIFICATION_STATUS)),
                record.get(q.VALID_FROM),
                record.get(q.VALID_TO),
                record.get(q.SUBMITTED_BY),
                record.get(q.SUBMITTED_AT),
                record.get(q.DECIDED_BY),
                record.get(q.DECIDED_AT),
                record.get(q.DECISION_REASON),
                record.get(q.AGGREGATE_VERSION));
    }

    private static ClearanceWorkItemView mapClearance(Record record) {
        NetClearanceWorkItem w = NET_CLEARANCE_WORK_ITEM;
        return new ClearanceWorkItemView(
                record.get(w.WORK_ITEM_ID),
                record.get(w.SUBJECT_TYPE),
                record.get(w.SERVICE_NETWORK_ID),
                record.get(w.TECHNICIAN_PROFILE_ID),
                record.get(w.WORK_ITEM_STATUS),
                record.get(w.REASON),
                record.get(w.OPEN_TASK_COUNT),
                record.get(w.OPEN_APPOINTMENT_COUNT),
                record.get(w.OPEN_VISIT_COUNT),
                record.get(w.ACTIVE_ASSIGNMENT_COUNT),
                record.get(w.OFFLINE_PACKAGE_COUNT),
                record.get(w.CREATED_BY),
                record.get(w.CREATED_AT),
                record.get(w.CORRELATION_ID));
    }
}
