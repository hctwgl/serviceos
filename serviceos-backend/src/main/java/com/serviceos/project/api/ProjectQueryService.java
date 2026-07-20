package com.serviceos.project.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.UUID;

/** Project 授权只读用例。 */
public interface ProjectQueryService {
    ProjectPage list(CurrentPrincipal principal, String correlationId, ProjectQuery query);

    ProjectDetail get(CurrentPrincipal principal, String correlationId, UUID projectId);

    ProjectScopeRelationRevisionPage listScopeRevisions(
            CurrentPrincipal principal,
            String correlationId,
            UUID projectId,
            String cursor,
            int limit
    );

    /**
     * 项目创建与范围编辑用的车企/区域选择选项（按实时授权范围聚合）。
     */
    ProjectReferenceOptions referenceOptions(CurrentPrincipal principal, String correlationId);

    /** 租户车企主数据目录；需要 project.read。 */
    ProjectClientDirectoryPage listClientDirectory(CurrentPrincipal principal, String correlationId);

    /** 行政区名称目录；需要 project.read。parentCode 为空表示根级，* 表示不限父级。 */
    RegionCatalogPage listRegionCatalog(
            CurrentPrincipal principal,
            String correlationId,
            String parentCode,
            String query,
            String level,
            Integer limit
    );
}
