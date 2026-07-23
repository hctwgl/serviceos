package com.serviceos.project.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.UUID;

public interface ProjectCommandService {
    ProjectView create(CurrentPrincipal principal, CommandMetadata metadata, CreateProjectCommand command);

    /** 将配置完成前的项目从 DRAFT 激活；需要 project.create，使用 If-Match 防止并发覆盖。 */
    ProjectView activate(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            UUID projectId,
            long expectedVersion
    );

    /** 登记/更新租户车企主数据；需要 project.create。 */
    ProjectClientDirectoryItem registerClient(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String clientCode,
            String displayName
    );

    /** 启用/停用车企；需要 project.create。 */
    ProjectClientDirectoryItem setClientStatus(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String clientCode,
            String status
    );

    /** 登记/更新车企品牌；需要 project.create。 */
    ProjectClientBrandItem registerBrand(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String clientCode,
            String brandCode,
            String displayName,
            Integer sortOrder
    );

    /** 启用/停用品牌；需要 project.create。 */
    ProjectClientBrandItem setBrandStatus(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String clientCode,
            String brandCode,
            String status
    );

    ProjectScopeRelationRevisionView reviseScopeRelations(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ReviseProjectScopeRelationsCommand command
    );
}
