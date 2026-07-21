package com.serviceos.project.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.project.api.CreateProjectCommand;
import com.serviceos.project.api.ProjectClientBrandItem;
import com.serviceos.project.api.ProjectClientDirectoryItem;
import com.serviceos.project.api.ProjectCommandService;
import com.serviceos.project.api.ProjectScopeRelationRevisionView;
import com.serviceos.project.api.ProjectView;
import com.serviceos.project.api.ReviseProjectScopeRelationsCommand;
import com.serviceos.project.domain.Project;
import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.BusinessProblem;
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
 * 创建项目的首条工程纵向切片。
 *
 * <p>事务顺序严格遵循 ARCH-19/20：幂等抢占 → 聚合写入 → 审计 → Outbox → 幂等结果。
 * 任何一步失败都会回滚全部写入，网络发布不在本事务内发生。</p>
 */
@Service
final class DefaultProjectCommandService implements ProjectCommandService {
    static final String OPERATION_TYPE = "project.create";
    static final String REVISE_SCOPE_OPERATION_TYPE = "project.reviseScopeRelations";

    private static final ObjectMapper CANONICAL_JSON = JsonMapper.builder()
            .findAndAddModules()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .build();

    private final ProjectRepository projects;
    private final ProjectCatalogRepository catalogs;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final Clock clock;

    DefaultProjectCommandService(
            ProjectRepository projects,
            ProjectCatalogRepository catalogs,
            AuthorizationService authorization,
            IdempotencyService idempotency,
            AuditAppender audit,
            OutboxAppender outbox,
            Clock clock
    ) {
        this.projects = projects;
        this.catalogs = catalogs;
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProjectView create(CurrentPrincipal principal, CommandMetadata metadata, CreateProjectCommand command) {
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        AuthorizationDecision authorizationDecision = authorization.require(
                principal,
                AuthorizationRequest.tenantCapability(
                        "project.create", context.tenantId(), "Project", command.code()),
                context.correlationId());

        String requestDigest = Sha256.digest(canonicalJson(command));
        IdempotencyDecision decision = idempotency.begin(context, OPERATION_TYPE, requestDigest);

        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            UUID projectId = UUID.fromString(decision.resourceId().orElseThrow());
            return projects.findById(context.tenantId(), projectId)
                    .map(Project::toView)
                    .orElseThrow(() -> new IllegalStateException(
                            "Idempotency result references a missing project"));
        }

        Instant now = clock.instant();
        Project project = Project.create(context.tenantId(), command, UUID.randomUUID(), now);
        catalogs.ensureClient(context.tenantId(), project.clientId(), project.clientId(), now);
        projects.insert(project);
        projects.insertRegionBindings(project, context.actorId());
        projects.insertNetworkBindings(project, context.actorId());

        String eventPayload = canonicalJson(new ProjectCreatedPayload(
                project.id(), project.tenantId(), project.code(), project.clientId(),
                project.name(), project.startsOn(), project.endsOn(), project.regionCodes(), project.networkIds(),
                project.status().name(),
                project.version(), project.createdAt()));
        String payloadDigest = Sha256.digest(eventPayload);
        UUID outboxId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        audit.append(new AuditEntry(
                UUID.randomUUID(), context.tenantId(), context.actorId(), "PROJECT_CREATED", "project.create",
                "Project", project.id().toString(), "ALLOW", authorizationDecision.matchedGrantIds(),
                authorizationDecision.policyVersion(), "SUCCEEDED", null, requestDigest,
                context.correlationId(), now));
        outbox.append(new OutboxEvent(
                outboxId, eventId, "project", "project.created", 3,
                "Project", project.id().toString(), project.version(), context.tenantId(),
                context.correlationId(), context.idempotencyKey(), project.id().toString(),
                eventPayload, payloadDigest, now));

        ProjectView result = project.toView();
        idempotency.complete(context, OPERATION_TYPE, project.id().toString(), Sha256.digest(canonicalJson(result)));
        return result;
    }

    @Override
    @Transactional
    public ProjectClientDirectoryItem registerClient(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String clientCode,
            String displayName
    ) {
        String code = requireText(clientCode, "clientCode", 128);
        String name = requireText(displayName, "displayName", 200);
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        AuthorizationDecision authorizationDecision = authorization.require(
                principal,
                AuthorizationRequest.tenantCapability(
                        "project.create", context.tenantId(), "ProjectClient", code),
                context.correlationId());
        String requestDigest = Sha256.digest(canonicalJson(new ClientRegisterPayload(code, name)));
        IdempotencyDecision decision = idempotency.begin(context, "project.registerClient", requestDigest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return catalogs.findClient(context.tenantId(), code)
                    .orElseThrow(() -> new IllegalStateException("Idempotency result missing client"));
        }
        Instant now = clock.instant();
        catalogs.upsertClient(context.tenantId(), code, name, "ACTIVE", now);
        ProjectClientDirectoryItem item = catalogs.findClient(context.tenantId(), code)
                .orElseThrow(() -> new IllegalStateException("Client upsert did not persist"));
        audit.append(new AuditEntry(
                UUID.randomUUID(), context.tenantId(), context.actorId(), "PROJECT_CLIENT_REGISTERED",
                "project.create", "ProjectClient", code, "ALLOW", authorizationDecision.matchedGrantIds(),
                authorizationDecision.policyVersion(), "SUCCEEDED", null, requestDigest,
                context.correlationId(), now));
        idempotency.complete(context, "project.registerClient", code, Sha256.digest(code + "|" + name));
        return item;
    }

    @Override
    @Transactional
    public ProjectClientDirectoryItem setClientStatus(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String clientCode,
            String status
    ) {
        String code = requireText(clientCode, "clientCode", 128);
        String nextStatus = requireCatalogStatus(status);
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        AuthorizationDecision authorizationDecision = authorization.require(
                principal,
                AuthorizationRequest.tenantCapability(
                        "project.create", context.tenantId(), "ProjectClient", code),
                context.correlationId());
        String requestDigest = Sha256.digest(canonicalJson(new CatalogStatusPayload(code, nextStatus)));
        IdempotencyDecision decision = idempotency.begin(context, "project.setClientStatus", requestDigest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return catalogs.findClient(context.tenantId(), code)
                    .orElseThrow(() -> new IllegalStateException("Idempotency result missing client"));
        }
        catalogs.findClient(context.tenantId(), code)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "车企不存在"));
        Instant now = clock.instant();
        catalogs.updateClientStatus(context.tenantId(), code, nextStatus, now);
        ProjectClientDirectoryItem item = catalogs.findClient(context.tenantId(), code)
                .orElseThrow(() -> new IllegalStateException("Client status update did not persist"));
        audit.append(new AuditEntry(
                UUID.randomUUID(), context.tenantId(), context.actorId(), "PROJECT_CLIENT_STATUS_CHANGED",
                "project.create", "ProjectClient", code, "ALLOW", authorizationDecision.matchedGrantIds(),
                authorizationDecision.policyVersion(), "SUCCEEDED", null, requestDigest,
                context.correlationId(), now));
        idempotency.complete(context, "project.setClientStatus", code, Sha256.digest(code + "|" + nextStatus));
        return item;
    }

    @Override
    @Transactional
    public ProjectClientBrandItem registerBrand(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String clientCode,
            String brandCode,
            String displayName,
            Integer sortOrder
    ) {
        String client = requireText(clientCode, "clientCode", 128);
        String brand = requireText(brandCode, "brandCode", 128);
        String name = requireText(displayName, "displayName", 200);
        int order = sortOrder == null ? 0 : sortOrder;
        if (order < 0 || order > 999_999) {
            throw new IllegalArgumentException("sortOrder is invalid");
        }
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        AuthorizationDecision authorizationDecision = authorization.require(
                principal,
                AuthorizationRequest.tenantCapability(
                        "project.create", context.tenantId(), "ProjectClientBrand", client + "/" + brand),
                context.correlationId());
        catalogs.findClient(context.tenantId(), client)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "车企不存在"));
        String requestDigest = Sha256.digest(canonicalJson(
                new BrandRegisterPayload(client, brand, name, order)));
        IdempotencyDecision decision = idempotency.begin(context, "project.registerBrand", requestDigest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return catalogs.findBrand(context.tenantId(), client, brand)
                    .orElseThrow(() -> new IllegalStateException("Idempotency result missing brand"));
        }
        Instant now = clock.instant();
        catalogs.upsertBrand(context.tenantId(), client, brand, name, "ACTIVE", order, now);
        ProjectClientBrandItem item = catalogs.findBrand(context.tenantId(), client, brand)
                .orElseThrow(() -> new IllegalStateException("Brand upsert did not persist"));
        audit.append(new AuditEntry(
                UUID.randomUUID(), context.tenantId(), context.actorId(), "PROJECT_CLIENT_BRAND_REGISTERED",
                "project.create", "ProjectClientBrand", client + "/" + brand, "ALLOW",
                authorizationDecision.matchedGrantIds(), authorizationDecision.policyVersion(),
                "SUCCEEDED", null, requestDigest, context.correlationId(), now));
        idempotency.complete(
                context, "project.registerBrand", client + "/" + brand,
                Sha256.digest(client + "|" + brand + "|" + name));
        return item;
    }

    @Override
    @Transactional
    public ProjectClientBrandItem setBrandStatus(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String clientCode,
            String brandCode,
            String status
    ) {
        String client = requireText(clientCode, "clientCode", 128);
        String brand = requireText(brandCode, "brandCode", 128);
        String nextStatus = requireCatalogStatus(status);
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        AuthorizationDecision authorizationDecision = authorization.require(
                principal,
                AuthorizationRequest.tenantCapability(
                        "project.create", context.tenantId(), "ProjectClientBrand", client + "/" + brand),
                context.correlationId());
        String requestDigest = Sha256.digest(canonicalJson(
                new BrandStatusPayload(client, brand, nextStatus)));
        IdempotencyDecision decision = idempotency.begin(context, "project.setBrandStatus", requestDigest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            return catalogs.findBrand(context.tenantId(), client, brand)
                    .orElseThrow(() -> new IllegalStateException("Idempotency result missing brand"));
        }
        catalogs.findBrand(context.tenantId(), client, brand)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "品牌不存在"));
        Instant now = clock.instant();
        catalogs.updateBrandStatus(context.tenantId(), client, brand, nextStatus, now);
        ProjectClientBrandItem item = catalogs.findBrand(context.tenantId(), client, brand)
                .orElseThrow(() -> new IllegalStateException("Brand status update did not persist"));
        audit.append(new AuditEntry(
                UUID.randomUUID(), context.tenantId(), context.actorId(), "PROJECT_CLIENT_BRAND_STATUS_CHANGED",
                "project.create", "ProjectClientBrand", client + "/" + brand, "ALLOW",
                authorizationDecision.matchedGrantIds(), authorizationDecision.policyVersion(),
                "SUCCEEDED", null, requestDigest, context.correlationId(), now));
        idempotency.complete(
                context, "project.setBrandStatus", client + "/" + brand,
                Sha256.digest(client + "|" + brand + "|" + nextStatus));
        return item;
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    private static String requireCatalogStatus(String status) {
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new IllegalArgumentException("status is invalid");
        }
        return status;
    }

    private record ClientRegisterPayload(String clientCode, String displayName) {
    }

    private record CatalogStatusPayload(String clientCode, String status) {
    }

    private record BrandRegisterPayload(
            String clientCode, String brandCode, String displayName, int sortOrder
    ) {
    }

    private record BrandStatusPayload(String clientCode, String brandCode, String status) {
    }

    /**
     * 整组关系修订必须在一个事务内完成版本竞争、有效期结束、关系追加、不可变收据、审计和 Outbox。
     * 任一步失败都回滚，避免授权解析看到“Project 已升级但关系只改了一半”的状态。
     */
    @Override
    @Transactional
    public ProjectScopeRelationRevisionView reviseScopeRelations(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ReviseProjectScopeRelationsCommand command
    ) {
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        AuthorizationDecision authorizationDecision = authorization.require(
                principal,
                AuthorizationRequest.projectCapability(
                        "project.reviseScopeRelations", context.tenantId(), "Project",
                        command.projectId().toString(), command.projectId().toString()),
                context.correlationId());

        String requestDigest = Sha256.digest(canonicalJson(command));
        IdempotencyDecision decision = idempotency.begin(
                context, REVISE_SCOPE_OPERATION_TYPE, requestDigest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            UUID revisionId = UUID.fromString(decision.resourceId().orElseThrow());
            return projects.findScopeRevision(context.tenantId(), revisionId)
                    .map(ProjectScopeRevision::toView)
                    .orElseThrow(() -> new IllegalStateException(
                            "幂等结果引用的项目范围修订不存在"));
        }

        Project current = projects.findByIdForUpdate(context.tenantId(), command.projectId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "项目不存在"));
        if (current.version() != command.expectedVersion()) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT,
                    "项目版本已变化，期望 " + command.expectedVersion() + "，实际 " + current.version());
        }

        Project revised = current.reviseScopeRelations(command.regionCodes(), command.networkIds());
        if (current.regionCodes().equals(revised.regionCodes())
                && current.networkIds().equals(revised.networkIds())) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "项目范围关系没有发生变化");
        }

        List<String> addedRegions = difference(revised.regionCodes(), current.regionCodes());
        List<String> removedRegions = difference(current.regionCodes(), revised.regionCodes());
        List<String> addedNetworks = difference(revised.networkIds(), current.networkIds());
        List<String> removedNetworks = difference(current.networkIds(), revised.networkIds());
        Instant now = clock.instant();

        if (!projects.advanceVersion(
                context.tenantId(), current.id(), command.expectedVersion())) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "项目版本已被并发修改");
        }
        projects.reviseRegionBindings(
                context.tenantId(), current.id(), removedRegions, addedRegions, context.actorId(), now);
        projects.reviseNetworkBindings(
                context.tenantId(), current.id(), removedNetworks, addedNetworks, context.actorId(), now);

        ProjectScopeRevision revision = new ProjectScopeRevision(
                UUID.randomUUID(), context.tenantId(), current.id(), current.version(), revised.version(),
                revised.regionCodes(), revised.networkIds(), addedRegions, removedRegions,
                addedNetworks, removedNetworks, command.reason(), context.actorId(), now);
        projects.insertScopeRevision(revision);

        String eventPayload = canonicalJson(new ProjectScopeRelationsRevisedPayload(
                revision.revisionId(), revision.projectId(), revision.tenantId(),
                revision.regionCodes(), revision.networkIds(),
                revision.addedRegionCodes(), revision.removedRegionCodes(),
                revision.addedNetworkIds(), revision.removedNetworkIds(),
                revision.reason(), revision.aggregateVersion(), revision.revisedAt()));
        audit.append(new AuditEntry(
                UUID.randomUUID(), context.tenantId(), context.actorId(),
                "PROJECT_SCOPE_RELATIONS_REVISED", REVISE_SCOPE_OPERATION_TYPE,
                "Project", current.id().toString(), "ALLOW", authorizationDecision.matchedGrantIds(),
                authorizationDecision.policyVersion(), "SUCCEEDED", command.reason(), requestDigest,
                context.correlationId(), now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "project", "project.scope-relations-revised", 1,
                "Project", current.id().toString(), revised.version(), context.tenantId(),
                context.correlationId(), context.idempotencyKey(), current.id().toString(),
                eventPayload, Sha256.digest(eventPayload), now));

        ProjectScopeRelationRevisionView result = revision.toView();
        idempotency.complete(
                context, REVISE_SCOPE_OPERATION_TYPE, revision.revisionId().toString(),
                Sha256.digest(canonicalJson(result)));
        return result;
    }

    private static List<String> difference(List<String> left, List<String> right) {
        return left.stream().filter(value -> !right.contains(value)).toList();
    }

    private static String canonicalJson(Object value) {
        try {
            return CANONICAL_JSON.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Command cannot be serialized", exception);
        }
    }

    private record ProjectCreatedPayload(
            UUID projectId,
            String tenantId,
            String code,
            String clientId,
            String name,
            java.time.LocalDate startsOn,
            java.time.LocalDate endsOn,
            java.util.List<String> regionCodes,
            java.util.List<String> networkIds,
            String status,
            long aggregateVersion,
            Instant occurredAt
    ) {
    }

    private record ProjectScopeRelationsRevisedPayload(
            UUID revisionId,
            UUID projectId,
            String tenantId,
            List<String> regionCodes,
            List<String> networkIds,
            List<String> addedRegionCodes,
            List<String> removedRegionCodes,
            List<String> addedNetworkIds,
            List<String> removedNetworkIds,
            String reason,
            long aggregateVersion,
            Instant occurredAt
    ) {
    }
}
