package com.serviceos.organization.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.PrincipalEmploymentLifecyclePort;
import com.serviceos.organization.api.DirectorySyncBatchView;
import com.serviceos.organization.api.OrgMembershipView;
import com.serviceos.organization.api.OrgUnitView;
import com.serviceos.organization.api.OrganizationAuthorizationEvidence;
import com.serviceos.organization.api.OrganizationAuthorizationPort;
import com.serviceos.organization.api.OrganizationCommandService;
import com.serviceos.organization.api.OrganizationCommandService.SyncItemInput;
import com.serviceos.organization.api.OrganizationRoleGrantPort;
import com.serviceos.organization.api.OrganizationView;
import com.serviceos.organization.domain.OrgMembership;
import com.serviceos.organization.domain.OrgUnit;
import com.serviceos.organization.domain.Organization;
import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 组织目录命令编排：授权 → 幂等 → 行锁 → 版本迁移 → 联动停用/撤权/待办 → 结构事件 → 审计。
 */
@Service
final class DefaultOrganizationCommandService implements OrganizationCommandService {
    private static final ObjectMapper CANONICAL_JSON = JsonMapper.builder()
            .findAndAddModules()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .build();

    private final OrganizationDirectoryRepository directory;
    private final OrganizationAuthorizationPort authorization;
    private final OrganizationRoleGrantPort roleGrants;
    private final PrincipalEmploymentLifecyclePort principalLifecycle;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final Clock clock;

    DefaultOrganizationCommandService(
            OrganizationDirectoryRepository directory,
            OrganizationAuthorizationPort authorization,
            OrganizationRoleGrantPort roleGrants,
            PrincipalEmploymentLifecyclePort principalLifecycle,
            IdempotencyService idempotency,
            AuditAppender audit,
            Clock clock
    ) {
        this.directory = directory;
        this.authorization = authorization;
        this.roleGrants = roleGrants;
        this.principalLifecycle = principalLifecycle;
        this.idempotency = idempotency;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public OrganizationView createOrganization(
            CurrentPrincipal actor, CommandMetadata metadata,
            String code, String name, String authorityMode,
            String sourceSystem, String sourceKey
    ) {
        code = requireText(code, "code", 64);
        name = requireText(name, "name", 200);
        Organization.AuthorityMode mode = requireAuthorityMode(authorityMode);
        sourceSystem = normalizeOptional(sourceSystem, "sourceSystem", 64);
        sourceKey = normalizeOptional(sourceKey, "sourceKey", 128);
        validateSource(mode, sourceSystem, sourceKey);
        var input = new CreateOrgInput(code, name, mode.name(), sourceSystem, sourceKey);
        CommandExecution execution = begin(actor, metadata, "organization.create", "organization.manageStructure",
                code, input);
        if (execution.replay()) {
            return findOrganizationByCode(actor.tenantId(), code).toView();
        }
        Instant now = clock.instant();
        UUID organizationId = UUID.randomUUID();
        Organization organization = new Organization(organizationId, actor.tenantId(), code, name, mode,
                Organization.Status.ACTIVE, sourceSystem, sourceKey, 1, now, now);
        directory.insertOrganization(organization);
        completeStructure(actor, metadata, execution, organizationId, organizationId, 1,
                "ORGANIZATION_CREATED", "Organization", "organization.manageStructure", null, now);
        return organization.toView();
    }

    @Override
    @Transactional
    public OrgUnitView createUnit(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID organizationId, long expectedOrgVersion,
            UUID parentUnitId, String unitCode, String unitName
    ) {
        requireExpectedVersion(expectedOrgVersion);
        unitCode = requireText(unitCode, "unitCode", 64);
        unitName = requireText(unitName, "unitName", 200);
        var input = new CreateUnitInput(organizationId, expectedOrgVersion, parentUnitId, unitCode, unitName);
        CommandExecution execution = begin(actor, metadata, "organization.createUnit", "organization.manageStructure",
                organizationId.toString(), input);
        if (execution.replay()) {
            UUID unitId = UUID.fromString(execution.decision().resourceId().orElseThrow());
            return requireUnit(actor.tenantId(), unitId).toView();
        }
        Organization organization = lockedOrganization(actor.tenantId(), organizationId, expectedOrgVersion);
        requireStructureWrite(actor, organization, metadata.correlationId());
        if (parentUnitId != null) {
            OrgUnit parent = requireUnit(actor.tenantId(), parentUnitId);
            if (!parent.organizationId().equals(organizationId)) {
                throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "父单元不存在");
            }
        }
        Instant now = clock.instant();
        UUID unitId = UUID.randomUUID();
        OrgUnit unit = new OrgUnit(unitId, actor.tenantId(), organizationId, parentUnitId, unitCode, unitName,
                OrgUnit.Status.ACTIVE, null, null, null, 1, now, now);
        directory.insertUnit(unit);
        directory.insertUnitClosure(actor.tenantId(), organizationId, unitId, parentUnitId);
        if (!directory.advanceOrganizationVersion(actor.tenantId(), organizationId, expectedOrgVersion, now)) {
            throw versionConflict();
        }
        completeStructure(actor, metadata, execution, organizationId, unitId, 1,
                "UNIT_CREATED", "OrgUnit", "organization.manageStructure", null, now, unitId.toString());
        return unit.toView();
    }

    @Override
    @Transactional
    public OrgUnitView moveUnit(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID organizationId, UUID unitId, long expectedUnitVersion, UUID newParentUnitId
    ) {
        requireExpectedVersion(expectedUnitVersion);
        var input = new MoveUnitInput(organizationId, unitId, expectedUnitVersion, newParentUnitId);
        CommandExecution execution = begin(actor, metadata, "organization.moveUnit", "organization.manageStructure",
                unitId.toString(), input);
        if (execution.replay()) {
            return requireUnit(actor.tenantId(), unitId).toView();
        }
        Organization organization = requireOrganization(actor.tenantId(), organizationId);
        requireStructureWrite(actor, organization, metadata.correlationId());
        OrgUnit unit = lockedUnit(actor.tenantId(), unitId, expectedUnitVersion);
        if (!unit.organizationId().equals(organizationId)) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "组织单元不存在");
        }
        if (newParentUnitId != null) {
            if (newParentUnitId.equals(unitId)) {
                throw new BusinessProblem(ProblemCode.ORGANIZATION_UNIT_CYCLE, "不能把单元移动到自己下面");
            }
            OrgUnit newParent = requireUnit(actor.tenantId(), newParentUnitId);
            if (!newParent.organizationId().equals(organizationId)) {
                throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "目标父单元不存在");
            }
            if (directory.isDescendant(actor.tenantId(), unitId, newParentUnitId)) {
                throw new BusinessProblem(ProblemCode.ORGANIZATION_UNIT_CYCLE, "不能把单元移动到其子孙节点下");
            }
        }
        Instant now = clock.instant();
        directory.rebuildClosureForMove(actor.tenantId(), organizationId, unitId, newParentUnitId);
        if (!directory.updateUnitParent(actor.tenantId(), unitId, expectedUnitVersion, newParentUnitId, now)) {
            throw versionConflict();
        }
        completeStructure(actor, metadata, execution, organizationId, unitId, expectedUnitVersion + 1,
                "UNIT_MOVED", "OrgUnit", "organization.manageStructure", null, now);
        return requireUnit(actor.tenantId(), unitId).toView();
    }

    @Override
    @Transactional
    public OrgMembershipView createMembership(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID organizationId, UUID unitId, UUID principalId,
            String membershipType, Instant validFrom
    ) {
        String type = requireMembershipType(membershipType);
        if (validFrom == null) throw new IllegalArgumentException("validFrom is invalid");
        var input = new CreateMembershipInput(organizationId, unitId, principalId, type, validFrom);
        CommandExecution execution = begin(actor, metadata, "organization.createMembership",
                "organization.manageMembership", principalId.toString(), input);
        if (execution.replay()) {
            UUID membershipId = UUID.fromString(execution.decision().resourceId().orElseThrow());
            return requireMembership(actor.tenantId(), membershipId).toView();
        }
        Organization organization = requireOrganization(actor.tenantId(), organizationId);
        requireMembershipWrite(actor, organization, metadata.correlationId());
        OrgUnit unit = requireUnit(actor.tenantId(), unitId);
        if (!unit.organizationId().equals(organizationId)) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "组织单元不存在");
        }
        Instant now = clock.instant();
        UUID membershipId = UUID.randomUUID();
        OrgMembership membership = new OrgMembership(membershipId, actor.tenantId(), organizationId, unitId,
                principalId, OrgMembership.MembershipType.valueOf(type), OrgMembership.Status.ACTIVE,
                validFrom, null, null, null, null, 1, actor.principalId(), now,
                null, null, null);
        directory.insertMembership(membership);
        completeStructure(actor, metadata, execution, organizationId, membershipId, 1,
                "MEMBERSHIP_CREATED", "OrgMembership", "organization.manageMembership", null, now,
                membershipId.toString());
        return membership.toView();
    }

    @Override
    @Transactional
    public OrgMembershipView transferMembership(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID membershipId, long expectedVersion,
            UUID targetUnitId, String membershipType, Instant validFrom
    ) {
        requireExpectedVersion(expectedVersion);
        String type = membershipType == null ? null : requireMembershipType(membershipType);
        var input = new TransferMembershipInput(membershipId, expectedVersion, targetUnitId, type, validFrom);
        CommandExecution execution = begin(actor, metadata, "organization.transferMembership",
                "organization.manageMembership", membershipId.toString(), input);
        if (execution.replay()) {
            return requireMembership(actor.tenantId(), membershipId).toView();
        }
        OrgMembership current = lockedMembership(actor.tenantId(), membershipId, expectedVersion);
        if (current.status() != OrgMembership.Status.ACTIVE) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "任职已终止");
        }
        Organization organization = requireOrganization(actor.tenantId(), current.organizationId());
        requireMembershipWrite(actor, organization, metadata.correlationId());
        OrgUnit target = requireUnit(actor.tenantId(), targetUnitId);
        if (!target.organizationId().equals(current.organizationId())) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "目标单元不存在");
        }
        Instant now = clock.instant();
        if (!directory.transferMembership(actor.tenantId(), membershipId, expectedVersion,
                targetUnitId, type, validFrom, actor.principalId(), now)) {
            throw versionConflict();
        }
        completeStructure(actor, metadata, execution, current.organizationId(), membershipId, expectedVersion + 1,
                "MEMBERSHIP_TRANSFERRED", "OrgMembership", "organization.manageMembership", null, now);
        return requireMembership(actor.tenantId(), membershipId).toView();
    }

    @Override
    @Transactional
    public OrgMembershipView terminateMembership(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID membershipId, long expectedVersion, String reason, boolean disablePrincipal
    ) {
        requireExpectedVersion(expectedVersion);
        reason = requireText(reason, "reason", 500);
        var input = new TerminateMembershipInput(membershipId, expectedVersion, reason, disablePrincipal);
        CommandExecution execution = begin(actor, metadata, "organization.terminateMembership",
                "organization.manageMembership", membershipId.toString(), input);
        if (execution.replay()) {
            return requireMembership(actor.tenantId(), membershipId).toView();
        }
        OrgMembership current = lockedMembership(actor.tenantId(), membershipId, expectedVersion);
        if (current.status() != OrgMembership.Status.ACTIVE) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "任职已终止");
        }
        Organization organization = requireOrganization(actor.tenantId(), current.organizationId());
        requireMembershipWrite(actor, organization, metadata.correlationId());
        Instant now = clock.instant();
        if (!directory.terminateMembership(actor.tenantId(), membershipId, expectedVersion,
                reason, actor.principalId(), now)) {
            throw versionConflict();
        }
        if (disablePrincipal) {
            principalLifecycle.disableForEmploymentTermination(
                    actor.tenantId(), current.principalId(), actor.principalId(), reason, metadata.correlationId());
            roleGrants.terminateActiveGrants(
                    actor.tenantId(), current.principalId().toString(), now,
                    actor.principalId(), reason, metadata.correlationId());
            directory.insertReassignmentWorkItem(UUID.randomUUID(), actor.tenantId(), current.organizationId(),
                    membershipId, current.principalId(), reason, actor.principalId(),
                    metadata.correlationId(), now);
        }
        completeStructure(actor, metadata, execution, current.organizationId(), membershipId, expectedVersion + 1,
                "MEMBERSHIP_TERMINATED", "OrgMembership", "organization.manageMembership", reason, now);
        return requireMembership(actor.tenantId(), membershipId).toView();
    }

    @Override
    @Transactional
    public DirectorySyncBatchView submitSyncBatch(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID organizationId, String sourceSystem, String externalBatchKey, List<SyncItemInput> items
    ) {
        sourceSystem = requireText(sourceSystem, "sourceSystem", 64);
        externalBatchKey = requireText(externalBatchKey, "externalBatchKey", 128);
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("items must not be empty");
        var input = new SyncBatchInput(organizationId, sourceSystem, externalBatchKey, items);
        CommandExecution execution = begin(actor, metadata, "organization.syncBatch", "organization.sync",
                organizationId.toString(), input);
        directory.lockSyncBatchKey(actor.tenantId(), sourceSystem, externalBatchKey);
        var existingBatchId = directory.findSyncBatchId(actor.tenantId(), sourceSystem, externalBatchKey);
        if (existingBatchId.isPresent()) {
            if (execution.replay()) {
                return directory.findSyncBatch(actor.tenantId(), existingBatchId.get()).orElseThrow();
            }
            throw new BusinessProblem(ProblemCode.ORGANIZATION_SYNC_CONFLICT, "同步批次已存在");
        }
        if (execution.replay()) {
            UUID batchId = UUID.fromString(execution.decision().resourceId().orElseThrow());
            return directory.findSyncBatch(actor.tenantId(), batchId).orElseThrow();
        }
        requireOrganization(actor.tenantId(), organizationId);
        Instant now = clock.instant();
        UUID batchId = UUID.randomUUID();
        directory.insertSyncBatch(batchId, actor.tenantId(), organizationId, sourceSystem, externalBatchKey,
                actor.principalId(), metadata.correlationId(), execution.requestDigest(), now);
        int success = 0, failed = 0, skipped = 0;
        for (int index = 0; index < items.size(); index++) {
            SyncItemInput item = items.get(index);
            ProcessResult result = processSyncItem(actor, organizationId, sourceSystem, item, now);
            directory.insertSyncItem(UUID.randomUUID(), batchId, actor.tenantId(), index,
                    item.operationType(), item.sourceKey(), item.externalVersion(),
                    result.status(), result.resultCode(), result.resultMessage(),
                    result.resourceType(), result.resourceId(), now);
            switch (result.status()) {
                case "SUCCESS" -> success++;
                case "FAILED" -> failed++;
                case "SKIPPED" -> skipped++;
                default -> throw new IllegalStateException("unknown sync item status");
            }
        }
        String batchStatus = failed > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED";
        directory.completeSyncBatch(batchId, batchStatus, success, failed, skipped, now);
        completeStructure(actor, metadata, execution, organizationId, batchId, 1,
                "SYNC_BATCH_COMPLETED", "DirectorySyncBatch", "organization.sync", null, now, batchId.toString());
        return directory.findSyncBatch(actor.tenantId(), batchId).orElseThrow();
    }

    private ProcessResult processSyncItem(
            CurrentPrincipal actor, UUID organizationId, String sourceSystem, SyncItemInput item, Instant now
    ) {
        try {
            return switch (item.operationType()) {
                case "UPSERT_UNIT" -> upsertUnitSync(actor, organizationId, sourceSystem, item, now);
                case "UPSERT_MEMBERSHIP" -> upsertMembershipSync(actor, organizationId, sourceSystem, item, now);
                case "TERMINATE_MEMBERSHIP" -> terminateMembershipSync(actor, sourceSystem, item, now);
                default -> new ProcessResult("FAILED", "INVALID_OPERATION", "未知操作类型", null, null);
            };
        } catch (BusinessProblem problem) {
            return new ProcessResult("FAILED", problem.code().name(), problem.getMessage(), null, null);
        } catch (RuntimeException exception) {
            return new ProcessResult("FAILED", "INTERNAL_ERROR", exception.getMessage(), null, null);
        }
    }

    private ProcessResult upsertUnitSync(
            CurrentPrincipal actor, UUID organizationId, String sourceSystem, SyncItemInput item, Instant now
    ) {
        Long existingVersion = directory.findUnitSourceVersion(actor.tenantId(), sourceSystem, item.sourceKey());
        if (existingVersion != null && existingVersion >= item.externalVersion()) {
            return new ProcessResult("SKIPPED", "OUT_OF_ORDER", "外部版本已更新", "OrgUnit", null);
        }
        UUID parentId = null;
        if (item.parentSourceKey() != null && !item.parentSourceKey().isBlank()) {
            parentId = directory.findUnitBySource(actor.tenantId(), sourceSystem, item.parentSourceKey())
                    .map(OrgUnit::id)
                    .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "父单元来源键不存在"));
        }
        UUID unitId = directory.findUnitBySource(actor.tenantId(), sourceSystem, item.sourceKey())
                .map(OrgUnit::id).orElse(UUID.randomUUID());
        directory.upsertUnitFromSync(unitId, actor.tenantId(), organizationId, parentId,
                requireText(item.unitCode(), "unitCode", 64),
                requireText(item.unitName(), "unitName", 200),
                sourceSystem, item.sourceKey(), item.externalVersion(), now);
        return new ProcessResult("SUCCESS", null, null, "OrgUnit", unitId);
    }

    private ProcessResult upsertMembershipSync(
            CurrentPrincipal actor, UUID organizationId, String sourceSystem, SyncItemInput item, Instant now
    ) {
        Long existingVersion = directory.findMembershipSourceVersion(actor.tenantId(), sourceSystem, item.sourceKey());
        if (existingVersion != null && existingVersion >= item.externalVersion()) {
            return new ProcessResult("SKIPPED", "OUT_OF_ORDER", "外部版本已更新", "OrgMembership", null);
        }
        if (item.principalId() == null) throw new IllegalArgumentException("principalId is invalid");
        String unitSourceKey = requireText(item.parentSourceKey(), "parentSourceKey", 128);
        UUID unitId = directory.findUnitBySource(actor.tenantId(), sourceSystem, unitSourceKey)
                .map(OrgUnit::id)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "任职单元来源键不存在"));
        UUID membershipId = directory.findMembershipBySource(actor.tenantId(), sourceSystem, item.sourceKey())
                .map(OrgMembership::id).orElse(UUID.randomUUID());
        directory.upsertMembershipFromSync(membershipId, actor.tenantId(), organizationId, unitId,
                item.principalId(), requireMembershipType(item.membershipType()),
                item.validFrom() == null ? now : item.validFrom(),
                sourceSystem, item.sourceKey(), item.externalVersion(), actor.principalId(), now);
        return new ProcessResult("SUCCESS", null, null, "OrgMembership", membershipId);
    }

    private ProcessResult terminateMembershipSync(
            CurrentPrincipal actor, String sourceSystem, SyncItemInput item, Instant now
    ) {
        Long existingVersion = directory.findMembershipSourceVersion(actor.tenantId(), sourceSystem, item.sourceKey());
        if (existingVersion != null && existingVersion >= item.externalVersion()) {
            return new ProcessResult("SKIPPED", "OUT_OF_ORDER", "外部版本已更新", "OrgMembership", null);
        }
        OrgMembership membership = directory.findMembershipBySource(actor.tenantId(), sourceSystem, item.sourceKey())
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "任职来源键不存在"));
        if (membership.status() == OrgMembership.Status.ACTIVE) {
            directory.terminateMembership(actor.tenantId(), membership.id(), membership.version(),
                    "外部同步终止", actor.principalId(), now);
        }
        return new ProcessResult("SUCCESS", null, null, "OrgMembership", membership.id());
    }

    private void requireStructureWrite(CurrentPrincipal actor, Organization organization, String correlationId) {
        if (organization.externalAuthoritative()) {
            authorization.requireTenantCapability(actor, "organization.overrideExternal",
                    organization.id().toString(), correlationId);
        }
        authorization.requireTenantCapability(actor, "organization.manageStructure",
                organization.id().toString(), correlationId);
    }

    private void requireMembershipWrite(CurrentPrincipal actor, Organization organization, String correlationId) {
        if (organization.externalAuthoritative()) {
            authorization.requireTenantCapability(actor, "organization.overrideExternal",
                    organization.id().toString(), correlationId);
        }
        authorization.requireTenantCapability(actor, "organization.manageMembership",
                organization.id().toString(), correlationId);
    }

    private CommandExecution begin(
            CurrentPrincipal actor, CommandMetadata metadata, String operation,
            String capability, String resourceId, Object input
    ) {
        OrganizationAuthorizationEvidence decision = authorization.requireTenantCapability(
                actor, capability, resourceId, metadata.correlationId());
        CommandContext context = new CommandContext(actor.tenantId(), actor.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        String requestDigest = Sha256.digest(canonicalJson(input));
        IdempotencyDecision idempotencyDecision = idempotency.begin(context, operation, requestDigest);
        return new CommandExecution(operation, requestDigest, decision, idempotencyDecision);
    }

    private void completeStructure(
            CurrentPrincipal actor, CommandMetadata metadata, CommandExecution execution,
            UUID organizationId, UUID resourceId, long resourceVersion, String eventType,
            String resourceType, String capability, String reason, Instant now
    ) {
        completeStructure(actor, metadata, execution, organizationId, resourceId, resourceVersion,
                eventType, resourceType, capability, reason, now, resourceId.toString());
    }

    private void completeStructure(
            CurrentPrincipal actor, CommandMetadata metadata, CommandExecution execution,
            UUID organizationId, UUID resourceId, long resourceVersion, String eventType,
            String resourceType, String capability, String reason, Instant now, String idempotencyResourceId
    ) {
        directory.insertStructureEvent(UUID.randomUUID(), actor.tenantId(), organizationId, eventType,
                resourceType, resourceId, resourceVersion, reason, actor.principalId(),
                execution.requestDigest(), metadata.correlationId(), now);
        audit.append(new AuditEntry(UUID.randomUUID(), actor.tenantId(), actor.principalId(),
                eventType, capability, resourceType, resourceId.toString(), "ALLOW",
                execution.authorization().matchedGrantIds(), execution.authorization().policyVersion(),
                "SUCCEEDED", null, execution.requestDigest(), metadata.correlationId(), now));
        CommandContext context = new CommandContext(actor.tenantId(), actor.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        idempotency.complete(context, execution.operation(), idempotencyResourceId,
                Sha256.digest(idempotencyResourceId + "|" + resourceVersion));
    }

    private Organization lockedOrganization(String tenantId, UUID organizationId, long expectedVersion) {
        Organization organization = directory.findOrganizationForUpdate(tenantId, organizationId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "组织不存在"));
        organization.requireActive();
        if (organization.version() != expectedVersion) throw versionConflict();
        return organization;
    }

    private Organization requireOrganization(String tenantId, UUID organizationId) {
        Organization organization = directory.findOrganization(tenantId, organizationId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "组织不存在"));
        organization.requireActive();
        return organization;
    }

    private Organization findOrganizationByCode(String tenantId, String code) {
        return directory.listOrganizations(tenantId).stream()
                .filter(org -> org.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("幂等结果引用的组织不存在"));
    }

    private OrgUnit lockedUnit(String tenantId, UUID unitId, long expectedVersion) {
        OrgUnit unit = directory.findUnitForUpdate(tenantId, unitId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "组织单元不存在"));
        if (unit.version() != expectedVersion) throw versionConflict();
        return unit;
    }

    private OrgUnit requireUnit(String tenantId, UUID unitId) {
        return directory.findUnit(tenantId, unitId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "组织单元不存在"));
    }

    private OrgMembership lockedMembership(String tenantId, UUID membershipId, long expectedVersion) {
        OrgMembership membership = directory.findMembershipForUpdate(tenantId, membershipId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "任职不存在"));
        if (membership.version() != expectedVersion) throw versionConflict();
        return membership;
    }

    private OrgMembership requireMembership(String tenantId, UUID membershipId) {
        return directory.findMembership(tenantId, membershipId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "任职不存在"));
    }

    private static boolean isReplay(IdempotencyDecision decision) {
        return decision.kind() == IdempotencyDecision.Kind.REPLAY;
    }

    private static void requireExpectedVersion(long version) {
        if (version < 1) throw new IllegalArgumentException("expectedVersion must be positive");
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    private static String normalizeOptional(String value, String field, int max) {
        if (value == null) return null;
        return requireText(value, field, max);
    }

    private static Organization.AuthorityMode requireAuthorityMode(String value) {
        try {
            return Organization.AuthorityMode.valueOf(requireText(value, "authorityMode", 32));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("authorityMode is invalid", exception);
        }
    }

    private static void validateSource(Organization.AuthorityMode mode, String sourceSystem, String sourceKey) {
        if (mode == Organization.AuthorityMode.LOCAL) {
            if (sourceSystem != null || sourceKey != null) {
                throw new IllegalArgumentException("LOCAL organization cannot carry source keys");
            }
        } else if (sourceSystem == null || sourceKey == null) {
            throw new IllegalArgumentException("EXTERNAL_AUTHORITATIVE organization requires source keys");
        }
    }

    private static String requireMembershipType(String value) {
        String normalized = requireText(value, "membershipType", 24);
        try {
            return OrgMembership.MembershipType.valueOf(normalized).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("membershipType is invalid", exception);
        }
    }

    private static BusinessProblem versionConflict() {
        return new BusinessProblem(ProblemCode.VERSION_CONFLICT, "版本已被并发修改");
    }

    private static String canonicalJson(Object value) {
        try {
            return CANONICAL_JSON.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("组织命令不能序列化", exception);
        }
    }

    private record CreateOrgInput(String code, String name, String authorityMode, String sourceSystem, String sourceKey) {}
    private record CreateUnitInput(UUID organizationId, long expectedOrgVersion, UUID parentUnitId, String unitCode, String unitName) {}
    private record MoveUnitInput(UUID organizationId, UUID unitId, long expectedUnitVersion, UUID newParentUnitId) {}
    private record CreateMembershipInput(UUID organizationId, UUID unitId, UUID principalId, String membershipType, Instant validFrom) {}
    private record TransferMembershipInput(UUID membershipId, long expectedVersion, UUID targetUnitId, String membershipType, Instant validFrom) {}
    private record TerminateMembershipInput(UUID membershipId, long expectedVersion, String reason, boolean disablePrincipal) {}
    private record SyncBatchInput(UUID organizationId, String sourceSystem, String externalBatchKey, List<SyncItemInput> items) {}
    private record ProcessResult(String status, String resultCode, String resultMessage, String resourceType, UUID resourceId) {}

    private record CommandExecution(
            String operation,
            String requestDigest,
            OrganizationAuthorizationEvidence authorization,
            IdempotencyDecision decision
    ) {
        boolean replay() { return isReplay(decision); }
    }
}
