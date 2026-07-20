package com.serviceos.network.infrastructure;

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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 网点目录 JDBC 适配器：成员/师傅唯一约束与清退待办依赖 PostgreSQL 精确语义。
 */
@Repository
final class JdbcNetworkDirectoryRepository implements NetworkDirectoryRepository {
    private static final String PARTNER_SELECT = """
            SELECT partner_organization_id, tenant_id, partner_code, partner_name,
                   partner_status, aggregate_version, created_at, updated_at
              FROM net_partner_organization
            """;
    private static final String NETWORK_SELECT = """
            SELECT service_network_id, tenant_id, partner_organization_id, network_code, network_name,
                   network_status, aggregate_version, created_at, updated_at,
                   deactivated_at, deactivated_by, deactivate_reason
              FROM net_service_network
            """;
    private static final String MEMBERSHIP_SELECT = """
            SELECT membership_id, tenant_id, service_network_id, principal_id, membership_role,
                   membership_status, valid_from, valid_to, invited_by, created_at,
                   terminated_by, terminated_at, terminate_reason, aggregate_version
              FROM net_network_membership
            """;
    private static final String TECHNICIAN_SELECT = """
            SELECT technician_profile_id, tenant_id, principal_id, display_name, profile_status,
                   supported_client_kinds::text AS supported_client_kinds,
                   aggregate_version, created_at, updated_at, disabled_at, disabled_by, disabled_reason
              FROM net_technician_profile
            """;
    private static final String TECH_MEMBERSHIP_SELECT = """
            SELECT membership_id, tenant_id, service_network_id, technician_profile_id, membership_status,
                   valid_from, valid_to, created_by, created_at, terminated_by, terminated_at,
                   terminate_reason, aggregate_version
              FROM net_network_technician_membership
            """;
    private static final String QUALIFICATION_SELECT = """
            SELECT qualification_id, tenant_id, technician_profile_id, qualification_code,
                   qualification_status, valid_from, valid_to, submitted_by, submitted_at,
                   decided_by, decided_at, decision_reason, aggregate_version
              FROM net_technician_qualification
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    JdbcNetworkDirectoryRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<PartnerOrganization> findPartner(String tenantId, UUID partnerOrganizationId) {
        return jdbc.sql(PARTNER_SELECT + " WHERE tenant_id=:tenant AND partner_organization_id=:id")
                .param("tenant", tenantId).param("id", partnerOrganizationId)
                .query(this::mapPartner).optional();
    }

    @Override
    public List<PartnerOrganization> listPartners(String tenantId) {
        return jdbc.sql(PARTNER_SELECT + " WHERE tenant_id=:tenant ORDER BY partner_code")
                .param("tenant", tenantId).query(this::mapPartner).list();
    }

    @Override
    public void insertPartner(PartnerOrganization partner) {
        try {
            jdbc.sql("""
                    INSERT INTO net_partner_organization (
                        partner_organization_id, tenant_id, partner_code, partner_name,
                        partner_status, aggregate_version, created_at, updated_at
                    ) VALUES (
                        :id, :tenant, :code, :name, :status, :version, :createdAt, :updatedAt
                    )
                    """)
                    .param("id", partner.id()).param("tenant", partner.tenantId())
                    .param("code", partner.code()).param("name", partner.name())
                    .param("status", partner.status().name()).param("version", partner.version())
                    .param("createdAt", dbTime(partner.createdAt())).param("updatedAt", dbTime(partner.updatedAt()))
                    .update();
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
        return jdbc.sql(NETWORK_SELECT + " WHERE tenant_id=:tenant AND service_network_id=:id"
                        + (forUpdate ? " FOR UPDATE" : ""))
                .param("tenant", tenantId).param("id", serviceNetworkId)
                .query(this::mapNetwork).optional();
    }

    @Override
    public List<ServiceNetwork> listNetworks(String tenantId, UUID partnerOrganizationId) {
        String sql = NETWORK_SELECT + " WHERE tenant_id=:tenant";
        var spec = jdbc.sql(sql + (partnerOrganizationId == null ? "" : " AND partner_organization_id=:partnerId")
                        + " ORDER BY network_code")
                .param("tenant", tenantId);
        if (partnerOrganizationId != null) {
            spec = spec.param("partnerId", partnerOrganizationId);
        }
        return spec.query(this::mapNetwork).list();
    }

    @Override
    public void insertNetwork(ServiceNetwork network) {
        try {
            jdbc.sql("""
                    INSERT INTO net_service_network (
                        service_network_id, tenant_id, partner_organization_id, network_code, network_name,
                        network_status, aggregate_version, created_at, updated_at
                    ) VALUES (
                        :id, :tenant, :partnerId, :code, :name, :status, :version, :createdAt, :updatedAt
                    )
                    """)
                    .param("id", network.id()).param("tenant", network.tenantId())
                    .param("partnerId", network.partnerOrganizationId())
                    .param("code", network.networkCode()).param("name", network.networkName())
                    .param("status", network.status().name()).param("version", network.version())
                    .param("createdAt", dbTime(network.createdAt())).param("updatedAt", dbTime(network.updatedAt()))
                    .update();
        } catch (DuplicateKeyException exception) {
            throw new BusinessProblem(ProblemCode.NETWORK_AUTHORITY_CONFLICT, "网点编码已存在");
        }
    }

    @Override
    public boolean deactivateNetwork(String tenantId, UUID serviceNetworkId, long expectedVersion,
            String reason, String actorId, Instant now) {
        return jdbc.sql("""
                UPDATE net_service_network
                   SET network_status='DEACTIVATED', aggregate_version=aggregate_version+1,
                       updated_at=:now, deactivated_at=:now, deactivated_by=:actor, deactivate_reason=:reason
                 WHERE tenant_id=:tenant AND service_network_id=:id
                   AND aggregate_version=:expected AND network_status='ACTIVE'
                """)
                .param("now", dbTime(now)).param("actor", actorId).param("reason", reason)
                .param("tenant", tenantId).param("id", serviceNetworkId).param("expected", expectedVersion)
                .update() == 1;
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
        return jdbc.sql(MEMBERSHIP_SELECT + " WHERE tenant_id=:tenant AND membership_id=:id"
                        + (forUpdate ? " FOR UPDATE" : ""))
                .param("tenant", tenantId).param("id", membershipId)
                .query(this::mapMembership).optional();
    }

    @Override
    public List<NetworkMembership> listMemberships(String tenantId, UUID serviceNetworkId, UUID principalId) {
        StringBuilder sql = new StringBuilder(MEMBERSHIP_SELECT + " WHERE tenant_id=:tenant");
        if (serviceNetworkId != null) {
            sql.append(" AND service_network_id=:networkId");
        }
        if (principalId != null) {
            sql.append(" AND principal_id=:principalId");
        }
        sql.append(" ORDER BY created_at");
        var spec = jdbc.sql(sql.toString()).param("tenant", tenantId);
        if (serviceNetworkId != null) {
            spec = spec.param("networkId", serviceNetworkId);
        }
        if (principalId != null) {
            spec = spec.param("principalId", principalId);
        }
        return spec.query(this::mapMembership).list();
    }

    @Override
    public void insertMembership(NetworkMembership membership) {
        try {
            jdbc.sql("""
                    INSERT INTO net_network_membership (
                        membership_id, tenant_id, service_network_id, principal_id, membership_role,
                        membership_status, valid_from, invited_by, created_at, aggregate_version
                    ) VALUES (
                        :id, :tenant, :networkId, :principalId, :role, :status, :validFrom,
                        :invitedBy, :createdAt, :version
                    )
                    """)
                    .param("id", membership.id()).param("tenant", membership.tenantId())
                    .param("networkId", membership.serviceNetworkId()).param("principalId", membership.principalId())
                    .param("role", membership.role().name()).param("status", membership.status().name())
                    .param("validFrom", dbTime(membership.validFrom())).param("invitedBy", membership.invitedBy())
                    .param("createdAt", dbTime(membership.createdAt())).param("version", membership.version())
                    .update();
        } catch (DuplicateKeyException exception) {
            throw new BusinessProblem(ProblemCode.NETWORK_MEMBERSHIP_CONFLICT, "该主体在此网点已有有效成员关系");
        }
    }

    @Override
    public boolean terminateMembership(String tenantId, UUID membershipId, long expectedVersion,
            String reason, String actorId, Instant terminatedAt) {
        return jdbc.sql("""
                UPDATE net_network_membership
                   SET membership_status='TERMINATED', aggregate_version=aggregate_version+1,
                       valid_to=:terminatedAt, terminated_by=:actor, terminated_at=:terminatedAt,
                       terminate_reason=:reason
                 WHERE tenant_id=:tenant AND membership_id=:id
                   AND aggregate_version=:expected AND membership_status='ACTIVE'
                """)
                .param("terminatedAt", dbTime(terminatedAt)).param("actor", actorId).param("reason", reason)
                .param("tenant", tenantId).param("id", membershipId).param("expected", expectedVersion)
                .update() == 1;
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
        return jdbc.sql(TECHNICIAN_SELECT + " WHERE tenant_id=:tenant AND technician_profile_id=:id"
                        + (forUpdate ? " FOR UPDATE" : ""))
                .param("tenant", tenantId).param("id", profileId)
                .query(this::mapTechnician).optional();
    }

    @Override
    public Optional<TechnicianProfile> findTechnicianProfileByPrincipal(String tenantId, UUID principalId) {
        return jdbc.sql(TECHNICIAN_SELECT + " WHERE tenant_id=:tenant AND principal_id=:principalId")
                .param("tenant", tenantId).param("principalId", principalId)
                .query(this::mapTechnician).optional();
    }

    @Override
    public List<TechnicianProfile> listTechnicianProfiles(String tenantId) {
        return jdbc.sql(TECHNICIAN_SELECT + " WHERE tenant_id=:tenant ORDER BY display_name")
                .param("tenant", tenantId).query(this::mapTechnician).list();
    }

    @Override
    public void insertTechnicianProfile(TechnicianProfile profile) {
        try {
            jdbc.sql("""
                    INSERT INTO net_technician_profile (
                        technician_profile_id, tenant_id, principal_id, display_name, profile_status,
                        supported_client_kinds, aggregate_version, created_at, updated_at
                    ) VALUES (
                        :id, :tenant, :principalId, :displayName, :status,
                        CAST(:supportedClientKinds AS jsonb), :version, :createdAt, :updatedAt
                    )
                    """)
                    .param("id", profile.id()).param("tenant", profile.tenantId())
                    .param("principalId", profile.principalId()).param("displayName", profile.displayName())
                    .param("status", profile.status().name())
                    .param("supportedClientKinds", writeKindsJson(profile.supportedClientKinds()))
                    .param("version", profile.version())
                    .param("createdAt", dbTime(profile.createdAt())).param("updatedAt", dbTime(profile.updatedAt()))
                    .update();
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
        return jdbc.sql("""
                UPDATE net_technician_profile
                   SET supported_client_kinds = CAST(:supportedClientKinds AS jsonb),
                       aggregate_version = aggregate_version + 1,
                       updated_at = :now
                 WHERE tenant_id = :tenant AND technician_profile_id = :id
                   AND aggregate_version = :expected
                """)
                .param("supportedClientKinds", writeKindsJson(supportedClientKinds))
                .param("now", dbTime(now))
                .param("tenant", tenantId)
                .param("id", profileId)
                .param("expected", expectedVersion)
                .update() == 1;
    }

    @Override
    public boolean disableTechnicianProfile(String tenantId, UUID profileId, long expectedVersion,
            String reason, String actorId, Instant now) {
        return jdbc.sql("""
                UPDATE net_technician_profile
                   SET profile_status='DISABLED', aggregate_version=aggregate_version+1,
                       updated_at=:now, disabled_at=:now, disabled_by=:actor, disabled_reason=:reason
                 WHERE tenant_id=:tenant AND technician_profile_id=:id
                   AND aggregate_version=:expected AND profile_status='ACTIVE'
                """)
                .param("now", dbTime(now)).param("actor", actorId).param("reason", reason)
                .param("tenant", tenantId).param("id", profileId).param("expected", expectedVersion)
                .update() == 1;
    }

    @Override
    public boolean enableTechnicianProfile(String tenantId, UUID profileId, long expectedVersion, Instant now) {
        return jdbc.sql("""
                UPDATE net_technician_profile
                   SET profile_status='ACTIVE', aggregate_version=aggregate_version+1,
                       updated_at=:now, disabled_at=NULL, disabled_by=NULL, disabled_reason=NULL
                 WHERE tenant_id=:tenant AND technician_profile_id=:id
                   AND aggregate_version=:expected AND profile_status='DISABLED'
                """)
                .param("now", dbTime(now)).param("tenant", tenantId).param("id", profileId)
                .param("expected", expectedVersion).update() == 1;
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
        return jdbc.sql(TECH_MEMBERSHIP_SELECT + " WHERE tenant_id=:tenant AND membership_id=:id"
                        + (forUpdate ? " FOR UPDATE" : ""))
                .param("tenant", tenantId).param("id", membershipId)
                .query(this::mapTechMembership).optional();
    }

    @Override
    public List<NetworkTechnicianMembership> listTechnicianMemberships(
            String tenantId, UUID serviceNetworkId, UUID technicianProfileId
    ) {
        StringBuilder sql = new StringBuilder(TECH_MEMBERSHIP_SELECT + " WHERE tenant_id=:tenant");
        if (serviceNetworkId != null) {
            sql.append(" AND service_network_id=:networkId");
        }
        if (technicianProfileId != null) {
            sql.append(" AND technician_profile_id=:profileId");
        }
        sql.append(" ORDER BY created_at");
        var spec = jdbc.sql(sql.toString()).param("tenant", tenantId);
        if (serviceNetworkId != null) {
            spec = spec.param("networkId", serviceNetworkId);
        }
        if (technicianProfileId != null) {
            spec = spec.param("profileId", technicianProfileId);
        }
        return spec.query(this::mapTechMembership).list();
    }

    @Override
    public Optional<NetworkTechnicianMembership> findActiveTechnicianMembership(
            String tenantId, UUID serviceNetworkId, UUID technicianProfileId, Instant at
    ) {
        return jdbc.sql(TECH_MEMBERSHIP_SELECT + """
                 WHERE tenant_id=:tenant AND service_network_id=:networkId
                   AND technician_profile_id=:profileId AND membership_status='ACTIVE'
                   AND valid_from <= :at AND (valid_to IS NULL OR valid_to > :at)
                """)
                .param("tenant", tenantId).param("networkId", serviceNetworkId)
                .param("profileId", technicianProfileId).param("at", dbTime(at))
                .query(this::mapTechMembership).optional();
    }

    @Override
    public void insertTechnicianMembership(NetworkTechnicianMembership membership) {
        try {
            jdbc.sql("""
                    INSERT INTO net_network_technician_membership (
                        membership_id, tenant_id, service_network_id, technician_profile_id,
                        membership_status, valid_from, created_by, created_at, aggregate_version
                    ) VALUES (
                        :id, :tenant, :networkId, :profileId, :status, :validFrom,
                        :createdBy, :createdAt, :version
                    )
                    """)
                    .param("id", membership.id()).param("tenant", membership.tenantId())
                    .param("networkId", membership.serviceNetworkId())
                    .param("profileId", membership.technicianProfileId())
                    .param("status", membership.status().name())
                    .param("validFrom", dbTime(membership.validFrom()))
                    .param("createdBy", membership.createdBy())
                    .param("createdAt", dbTime(membership.createdAt()))
                    .param("version", membership.version())
                    .update();
        } catch (DuplicateKeyException exception) {
            throw new BusinessProblem(ProblemCode.NETWORK_TECHNICIAN_CONFLICT, "该师傅在此网点已有有效服务关系");
        }
    }

    @Override
    public boolean terminateTechnicianMembership(String tenantId, UUID membershipId, long expectedVersion,
            String reason, String actorId, Instant terminatedAt) {
        return jdbc.sql("""
                UPDATE net_network_technician_membership
                   SET membership_status='TERMINATED', aggregate_version=aggregate_version+1,
                       valid_to=:terminatedAt, terminated_by=:actor, terminated_at=:terminatedAt,
                       terminate_reason=:reason
                 WHERE tenant_id=:tenant AND membership_id=:id
                   AND aggregate_version=:expected AND membership_status='ACTIVE'
                """)
                .param("terminatedAt", dbTime(terminatedAt)).param("actor", actorId).param("reason", reason)
                .param("tenant", tenantId).param("id", membershipId).param("expected", expectedVersion)
                .update() == 1;
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
        return jdbc.sql(QUALIFICATION_SELECT + " WHERE tenant_id=:tenant AND qualification_id=:id"
                        + (forUpdate ? " FOR UPDATE" : ""))
                .param("tenant", tenantId).param("id", qualificationId)
                .query(this::mapQualification).optional();
    }

    @Override
    public List<TechnicianQualification> listQualifications(String tenantId, UUID technicianProfileId) {
        return jdbc.sql(QUALIFICATION_SELECT + """
                 WHERE tenant_id=:tenant AND technician_profile_id=:profileId
                 ORDER BY submitted_at DESC
                """)
                .param("tenant", tenantId).param("profileId", technicianProfileId)
                .query(this::mapQualification).list();
    }

    @Override
    public boolean hasApprovedQualification(String tenantId, UUID technicianProfileId, Instant at) {
        Long count = jdbc.sql("""
                        SELECT count(*)
                          FROM net_technician_qualification
                         WHERE tenant_id=:tenant AND technician_profile_id=:profileId
                           AND qualification_status='APPROVED'
                           AND valid_from <= :at AND (valid_to IS NULL OR valid_to > :at)
                        """)
                .param("tenant", tenantId).param("profileId", technicianProfileId).param("at", dbTime(at))
                .query(Long.class).single();
        return count != null && count > 0;
    }

    @Override
    public void insertQualification(TechnicianQualification qualification) {
        jdbc.sql("""
                INSERT INTO net_technician_qualification (
                    qualification_id, tenant_id, technician_profile_id, qualification_code,
                    qualification_status, valid_from, valid_to, submitted_by, submitted_at, aggregate_version
                ) VALUES (
                    :id, :tenant, :profileId, :code, :status, :validFrom, :validTo,
                    :submittedBy, :submittedAt, :version
                )
                """)
                .param("id", qualification.id()).param("tenant", qualification.tenantId())
                .param("profileId", qualification.technicianProfileId())
                .param("code", qualification.qualificationCode())
                .param("status", qualification.status().name())
                .param("validFrom", dbTime(qualification.validFrom()))
                .param("validTo", qualification.validTo() == null ? null : dbTime(qualification.validTo()))
                .param("submittedBy", qualification.submittedBy())
                .param("submittedAt", dbTime(qualification.submittedAt()))
                .param("version", qualification.version())
                .update();
    }

    @Override
    public boolean decideQualification(String tenantId, UUID qualificationId, long expectedVersion,
            String status, String reason, String actorId, Instant now) {
        return jdbc.sql("""
                UPDATE net_technician_qualification
                   SET qualification_status=:status, aggregate_version=aggregate_version+1,
                       decided_by=:actor, decided_at=:now, decision_reason=:reason
                 WHERE tenant_id=:tenant AND qualification_id=:id
                   AND aggregate_version=:expected AND qualification_status='PENDING'
                """)
                .param("status", status).param("actor", actorId).param("now", dbTime(now))
                .param("reason", reason).param("tenant", tenantId).param("id", qualificationId)
                .param("expected", expectedVersion).update() == 1;
    }

    @Override
    public void insertDirectoryEvent(UUID eventId, String tenantId, String eventType, String resourceType,
            UUID resourceId, long resourceVersion, String reason, String actorId,
            String requestDigest, String correlationId, Instant occurredAt) {
        jdbc.sql("""
                INSERT INTO net_directory_event (
                    directory_event_id, tenant_id, event_type, resource_type, resource_id,
                    resource_version, reason, actor_id, request_digest, correlation_id, occurred_at
                ) VALUES (
                    :id, :tenant, :eventType, :resourceType, :resourceId, :resourceVersion,
                    :reason, :actorId, :digest, :correlationId, :occurredAt
                )
                """)
                .param("id", eventId).param("tenant", tenantId).param("eventType", eventType)
                .param("resourceType", resourceType).param("resourceId", resourceId)
                .param("resourceVersion", resourceVersion).param("reason", reason)
                .param("actorId", actorId).param("digest", requestDigest)
                .param("correlationId", correlationId).param("occurredAt", dbTime(occurredAt))
                .update();
    }

    @Override
    public void insertClearanceWorkItem(UUID workItemId, String tenantId, String subjectType,
            UUID serviceNetworkId, UUID technicianProfileId, String reason,
            int openTasks, int openAppointments, int openVisits, int activeAssignments, int offlinePackages,
            String createdBy, String correlationId, Instant createdAt) {
        jdbc.sql("""
                INSERT INTO net_clearance_work_item (
                    work_item_id, tenant_id, subject_type, service_network_id, technician_profile_id,
                    work_item_status, reason, open_task_count, open_appointment_count, open_visit_count,
                    active_assignment_count, offline_package_count, created_by, created_at, correlation_id
                ) VALUES (
                    :id, :tenant, :subjectType, :networkId, :profileId, 'OPEN', :reason,
                    :openTasks, :openAppointments, :openVisits, :activeAssignments, :offlinePackages,
                    :createdBy, :createdAt, :correlationId
                )
                """)
                .param("id", workItemId).param("tenant", tenantId).param("subjectType", subjectType)
                .param("networkId", serviceNetworkId).param("profileId", technicianProfileId)
                .param("reason", reason).param("openTasks", openTasks)
                .param("openAppointments", openAppointments).param("openVisits", openVisits)
                .param("activeAssignments", activeAssignments).param("offlinePackages", offlinePackages)
                .param("createdBy", createdBy).param("createdAt", dbTime(createdAt))
                .param("correlationId", correlationId).update();
    }

    @Override
    public List<ClearanceWorkItemView> listOpenClearanceWorkItems(String tenantId) {
        return jdbc.sql("""
                SELECT work_item_id, subject_type, service_network_id, technician_profile_id,
                       work_item_status, reason, open_task_count, open_appointment_count, open_visit_count,
                       active_assignment_count, offline_package_count, created_by, created_at, correlation_id
                  FROM net_clearance_work_item
                 WHERE tenant_id=:tenant AND work_item_status='OPEN'
                 ORDER BY created_at
                """)
                .param("tenant", tenantId).query(this::mapClearance).list();
    }

    private PartnerOrganization mapPartner(ResultSet rs, int rowNum) throws SQLException {
        return new PartnerOrganization(
                rs.getObject("partner_organization_id", UUID.class),
                rs.getString("tenant_id"),
                rs.getString("partner_code"),
                rs.getString("partner_name"),
                PartnerOrganization.Status.valueOf(rs.getString("partner_status")),
                rs.getLong("aggregate_version"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
    }

    private ServiceNetwork mapNetwork(ResultSet rs, int rowNum) throws SQLException {
        return new ServiceNetwork(
                rs.getObject("service_network_id", UUID.class),
                rs.getString("tenant_id"),
                rs.getObject("partner_organization_id", UUID.class),
                rs.getString("network_code"),
                rs.getString("network_name"),
                ServiceNetwork.Status.valueOf(rs.getString("network_status")),
                rs.getLong("aggregate_version"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")),
                toInstant(rs.getTimestamp("deactivated_at")),
                rs.getString("deactivated_by"),
                rs.getString("deactivate_reason"));
    }

    private NetworkMembership mapMembership(ResultSet rs, int rowNum) throws SQLException {
        return new NetworkMembership(
                rs.getObject("membership_id", UUID.class),
                rs.getString("tenant_id"),
                rs.getObject("service_network_id", UUID.class),
                rs.getObject("principal_id", UUID.class),
                NetworkMembership.Role.valueOf(rs.getString("membership_role")),
                NetworkMembership.Status.valueOf(rs.getString("membership_status")),
                toInstant(rs.getTimestamp("valid_from")),
                toInstant(rs.getTimestamp("valid_to")),
                rs.getString("invited_by"),
                toInstant(rs.getTimestamp("created_at")),
                rs.getString("terminated_by"),
                toInstant(rs.getTimestamp("terminated_at")),
                rs.getString("terminate_reason"),
                rs.getLong("aggregate_version"));
    }

    private TechnicianProfile mapTechnician(ResultSet rs, int rowNum) throws SQLException {
        return new TechnicianProfile(
                rs.getObject("technician_profile_id", UUID.class),
                rs.getString("tenant_id"),
                rs.getObject("principal_id", UUID.class),
                rs.getString("display_name"),
                TechnicianProfile.Status.valueOf(rs.getString("profile_status")),
                readKinds(rs.getString("supported_client_kinds")),
                rs.getLong("aggregate_version"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")),
                toInstant(rs.getTimestamp("disabled_at")),
                rs.getString("disabled_by"),
                rs.getString("disabled_reason"));
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

    private NetworkTechnicianMembership mapTechMembership(ResultSet rs, int rowNum) throws SQLException {
        return new NetworkTechnicianMembership(
                rs.getObject("membership_id", UUID.class),
                rs.getString("tenant_id"),
                rs.getObject("service_network_id", UUID.class),
                rs.getObject("technician_profile_id", UUID.class),
                NetworkTechnicianMembership.Status.valueOf(rs.getString("membership_status")),
                toInstant(rs.getTimestamp("valid_from")),
                toInstant(rs.getTimestamp("valid_to")),
                rs.getString("created_by"),
                toInstant(rs.getTimestamp("created_at")),
                rs.getString("terminated_by"),
                toInstant(rs.getTimestamp("terminated_at")),
                rs.getString("terminate_reason"),
                rs.getLong("aggregate_version"));
    }

    private TechnicianQualification mapQualification(ResultSet rs, int rowNum) throws SQLException {
        return new TechnicianQualification(
                rs.getObject("qualification_id", UUID.class),
                rs.getString("tenant_id"),
                rs.getObject("technician_profile_id", UUID.class),
                rs.getString("qualification_code"),
                TechnicianQualification.Status.valueOf(rs.getString("qualification_status")),
                toInstant(rs.getTimestamp("valid_from")),
                toInstant(rs.getTimestamp("valid_to")),
                rs.getString("submitted_by"),
                toInstant(rs.getTimestamp("submitted_at")),
                rs.getString("decided_by"),
                toInstant(rs.getTimestamp("decided_at")),
                rs.getString("decision_reason"),
                rs.getLong("aggregate_version"));
    }

    private ClearanceWorkItemView mapClearance(ResultSet rs, int rowNum) throws SQLException {
        return new ClearanceWorkItemView(
                rs.getObject("work_item_id", UUID.class),
                rs.getString("subject_type"),
                rs.getObject("service_network_id", UUID.class),
                rs.getObject("technician_profile_id", UUID.class),
                rs.getString("work_item_status"),
                rs.getString("reason"),
                rs.getInt("open_task_count"),
                rs.getInt("open_appointment_count"),
                rs.getInt("open_visit_count"),
                rs.getInt("active_assignment_count"),
                rs.getInt("offline_package_count"),
                rs.getString("created_by"),
                toInstant(rs.getTimestamp("created_at")),
                rs.getString("correlation_id"));
    }

    private static Timestamp dbTime(Instant instant) {
        return Timestamp.from(instant.atOffset(ZoneOffset.UTC).toInstant());
    }

    private static Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(timestamp.toInstant(), ZoneOffset.UTC).toInstant();
    }
}
