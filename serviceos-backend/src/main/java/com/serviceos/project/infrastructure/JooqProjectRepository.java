package com.serviceos.project.infrastructure;

import com.serviceos.jooq.generated.tables.PrjProject;
import com.serviceos.project.application.ProjectRepository;
import com.serviceos.project.application.ProjectScopeRevision;
import com.serviceos.project.domain.Project;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.PrjProject.PRJ_PROJECT;
import static com.serviceos.jooq.generated.tables.PrjProjectNetwork.PRJ_PROJECT_NETWORK;
import static com.serviceos.jooq.generated.tables.PrjProjectRegion.PRJ_PROJECT_REGION;
import static com.serviceos.jooq.generated.tables.PrjProjectScopeRevision.PRJ_PROJECT_SCOPE_REVISION;

@Repository
final class JooqProjectRepository implements ProjectRepository {
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final DSLContext dsl;

    JooqProjectRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void insert(Project project) {
        PrjProject p = PRJ_PROJECT;
        dsl.insertInto(p)
                .set(p.PROJECT_ID, project.id())
                .set(p.TENANT_ID, project.tenantId())
                .set(p.PROJECT_CODE, project.code())
                .set(p.CLIENT_ID, project.clientId())
                .set(p.PROJECT_NAME, project.name())
                .set(p.STARTS_ON, project.startsOn())
                .set(p.ENDS_ON, project.endsOn())
                .set(p.PROJECT_STATUS, project.status().name())
                .set(p.AGGREGATE_VERSION, project.version())
                .set(p.CREATED_AT, project.createdAt())
                .execute();
    }

    @Override
    public void insertRegionBindings(Project project, String createdBy) {
        for (String regionCode : project.regionCodes()) {
            insertRegionBinding(project.tenantId(), project.id(), regionCode, createdBy, project.createdAt());
        }
    }

    @Override
    public void insertNetworkBindings(Project project, String createdBy) {
        for (String networkId : project.networkIds()) {
            insertNetworkBinding(project.tenantId(), project.id(), networkId, createdBy, project.createdAt());
        }
    }

    @Override
    public Optional<Project> findById(String tenantId, UUID projectId) {
        return find(tenantId, projectId, false);
    }

    @Override
    public Optional<Project> findByIdForUpdate(String tenantId, UUID projectId) {
        return find(tenantId, projectId, true);
    }

    private Optional<Project> find(String tenantId, UUID projectId, boolean forUpdate) {
        PrjProject p = PRJ_PROJECT;
        var query = dsl.select(
                        p.PROJECT_ID, p.TENANT_ID, p.PROJECT_CODE, p.CLIENT_ID, p.PROJECT_NAME,
                        p.STARTS_ON, p.ENDS_ON, p.PROJECT_STATUS, p.AGGREGATE_VERSION, p.CREATED_AT)
                .from(p)
                .where(p.TENANT_ID.eq(tenantId))
                .and(p.PROJECT_ID.eq(projectId));
        // 原 SQL 对整行加 FOR UPDATE 行锁，jOOQ 保持同一锁定语义。
        if (forUpdate) {
            return query.forUpdate().fetchOptional(record -> project(record, tenantId, projectId));
        }
        return query.fetchOptional(record -> project(record, tenantId, projectId));
    }

    private Project project(org.jooq.Record record, String tenantId, UUID projectId) {
        PrjProject p = PRJ_PROJECT;
        return new Project(
                record.get(p.PROJECT_ID), record.get(p.TENANT_ID), record.get(p.PROJECT_CODE),
                record.get(p.CLIENT_ID), record.get(p.PROJECT_NAME),
                record.get(p.STARTS_ON), record.get(p.ENDS_ON),
                findActiveRegions(tenantId, projectId), findOpenNetworkBindings(tenantId, projectId),
                Project.Status.valueOf(record.get(p.PROJECT_STATUS)),
                record.get(p.AGGREGATE_VERSION), record.get(p.CREATED_AT));
    }

    @Override
    public boolean advanceVersion(String tenantId, UUID projectId, long expectedVersion) {
        PrjProject p = PRJ_PROJECT;
        // 乐观并发：版本条件不匹配即影响 0 行，失败关闭。
        return dsl.update(p)
                .set(p.AGGREGATE_VERSION, p.AGGREGATE_VERSION.plus(1))
                .where(p.TENANT_ID.eq(tenantId))
                .and(p.PROJECT_ID.eq(projectId))
                .and(p.AGGREGATE_VERSION.eq(expectedVersion))
                .execute() == 1;
    }

    @Override
    public void reviseRegionBindings(
            String tenantId, UUID projectId, List<String> removed, List<String> added,
            String actorId, Instant revisedAt
    ) {
        var r = PRJ_PROJECT_REGION;
        for (String regionCode : removed) {
            int updated = dsl.update(r)
                    .set(r.VALID_TO, revisedAt)
                    .set(r.ENDED_BY, actorId)
                    .set(r.ENDED_AT, revisedAt)
                    .where(r.TENANT_ID.eq(tenantId))
                    .and(r.PROJECT_ID.eq(projectId))
                    .and(r.REGION_CODE.eq(regionCode))
                    .and(r.VALID_TO.isNull())
                    .execute();
            if (updated != 1) {
                throw new IllegalStateException("待终止的项目 REGION 关系不存在或不唯一");
            }
        }
        for (String regionCode : added) {
            insertRegionBinding(tenantId, projectId, regionCode, actorId, revisedAt);
        }
    }

    @Override
    public void reviseNetworkBindings(
            String tenantId, UUID projectId, List<String> removed, List<String> added,
            String actorId, Instant revisedAt
    ) {
        var n = PRJ_PROJECT_NETWORK;
        for (String networkId : removed) {
            int updated = dsl.update(n)
                    .set(n.VALID_TO, revisedAt)
                    .set(n.ENDED_BY, actorId)
                    .set(n.ENDED_AT, revisedAt)
                    .where(n.TENANT_ID.eq(tenantId))
                    .and(n.PROJECT_ID.eq(projectId))
                    .and(n.NETWORK_ID.eq(networkId))
                    .and(n.VALID_TO.isNull())
                    .execute();
            if (updated != 1) {
                throw new IllegalStateException("待终止的项目 NETWORK 关系不存在或不唯一");
            }
        }
        for (String networkId : added) {
            insertNetworkBinding(tenantId, projectId, networkId, actorId, revisedAt);
        }
    }

    private void insertRegionBinding(
            String tenantId, UUID projectId, String regionCode, String actorId, Instant validFrom
    ) {
        var r = PRJ_PROJECT_REGION;
        dsl.insertInto(r)
                .set(r.PROJECT_REGION_ID, UUID.randomUUID())
                .set(r.TENANT_ID, tenantId)
                .set(r.PROJECT_ID, projectId)
                .set(r.REGION_CODE, regionCode)
                .set(r.VALID_FROM, validFrom)
                .set(r.CREATED_BY, actorId)
                .set(r.CREATED_AT, validFrom)
                .execute();
    }

    private void insertNetworkBinding(
            String tenantId, UUID projectId, String networkId, String actorId, Instant validFrom
    ) {
        var n = PRJ_PROJECT_NETWORK;
        dsl.insertInto(n)
                .set(n.PROJECT_NETWORK_ID, UUID.randomUUID())
                .set(n.TENANT_ID, tenantId)
                .set(n.PROJECT_ID, projectId)
                .set(n.NETWORK_ID, networkId)
                .set(n.VALID_FROM, validFrom)
                .set(n.CREATED_BY, actorId)
                .set(n.CREATED_AT, validFrom)
                .execute();
    }

    @Override
    public void insertScopeRevision(ProjectScopeRevision revision) {
        var rev = PRJ_PROJECT_SCOPE_REVISION;
        // jsonb 列由生成物 JsonbStringConverter 绑定，直接写字符串，不再需要 CAST。
        dsl.insertInto(rev)
                .set(rev.REVISION_ID, revision.revisionId())
                .set(rev.TENANT_ID, revision.tenantId())
                .set(rev.PROJECT_ID, revision.projectId())
                .set(rev.EXPECTED_VERSION, revision.expectedVersion())
                .set(rev.AGGREGATE_VERSION, revision.aggregateVersion())
                .set(rev.REGION_CODES, json(revision.regionCodes()))
                .set(rev.NETWORK_IDS, json(revision.networkIds()))
                .set(rev.ADDED_REGION_CODES, json(revision.addedRegionCodes()))
                .set(rev.REMOVED_REGION_CODES, json(revision.removedRegionCodes()))
                .set(rev.ADDED_NETWORK_IDS, json(revision.addedNetworkIds()))
                .set(rev.REMOVED_NETWORK_IDS, json(revision.removedNetworkIds()))
                .set(rev.REASON, revision.reason())
                .set(rev.REVISED_BY, revision.revisedBy())
                .set(rev.REVISED_AT, revision.revisedAt())
                .execute();
    }

    @Override
    public Optional<ProjectScopeRevision> findScopeRevision(String tenantId, UUID revisionId) {
        var rev = PRJ_PROJECT_SCOPE_REVISION;
        return dsl.select(
                        rev.REVISION_ID, rev.TENANT_ID, rev.PROJECT_ID, rev.EXPECTED_VERSION,
                        rev.AGGREGATE_VERSION, rev.REGION_CODES, rev.NETWORK_IDS,
                        rev.ADDED_REGION_CODES, rev.REMOVED_REGION_CODES,
                        rev.ADDED_NETWORK_IDS, rev.REMOVED_NETWORK_IDS,
                        rev.REASON, rev.REVISED_BY, rev.REVISED_AT)
                .from(rev)
                .where(rev.TENANT_ID.eq(tenantId))
                .and(rev.REVISION_ID.eq(revisionId))
                .fetchOptional(JooqProjectRepository::revision);
    }

    static ProjectScopeRevision revision(org.jooq.Record record) {
        var rev = PRJ_PROJECT_SCOPE_REVISION;
        return new ProjectScopeRevision(
                record.get(rev.REVISION_ID), record.get(rev.TENANT_ID), record.get(rev.PROJECT_ID),
                record.get(rev.EXPECTED_VERSION), record.get(rev.AGGREGATE_VERSION),
                jsonList(record.get(rev.REGION_CODES)), jsonList(record.get(rev.NETWORK_IDS)),
                jsonList(record.get(rev.ADDED_REGION_CODES)),
                jsonList(record.get(rev.REMOVED_REGION_CODES)),
                jsonList(record.get(rev.ADDED_NETWORK_IDS)),
                jsonList(record.get(rev.REMOVED_NETWORK_IDS)),
                record.get(rev.REASON), record.get(rev.REVISED_BY), record.get(rev.REVISED_AT));
    }

    private static String json(List<String> values) {
        try {
            return JSON.writeValueAsString(values);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("项目范围关系不能序列化", exception);
        }
    }

    static List<String> jsonList(String value) {
        try {
            return List.of(JSON.readValue(value, String[].class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("项目范围修订包含非法 JSON", exception);
        }
    }

    private List<String> findActiveRegions(String tenantId, UUID projectId) {
        var r = PRJ_PROJECT_REGION;
        return dsl.select(r.REGION_CODE)
                .from(r)
                .where(r.TENANT_ID.eq(tenantId))
                .and(r.PROJECT_ID.eq(projectId))
                .and(r.VALID_TO.isNull())
                .orderBy(r.REGION_CODE)
                .fetch(r.REGION_CODE);
    }

    private List<String> findOpenNetworkBindings(String tenantId, UUID projectId) {
        var n = PRJ_PROJECT_NETWORK;
        return dsl.select(n.NETWORK_ID)
                .from(n)
                .where(n.TENANT_ID.eq(tenantId))
                .and(n.PROJECT_ID.eq(projectId))
                .and(n.VALID_TO.isNull())
                .orderBy(n.NETWORK_ID)
                .fetch(n.NETWORK_ID);
    }
}
