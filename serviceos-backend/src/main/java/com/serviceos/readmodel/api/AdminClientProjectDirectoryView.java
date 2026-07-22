package com.serviceos.readmodel.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Admin 客户与项目页面级目录，统一返回业务名称、范围和履约配置摘要。 */
public record AdminClientProjectDirectoryView(
        List<ClientItem> clients,
        List<ProjectItem> projects,
        Instant asOf
) {
    public AdminClientProjectDirectoryView {
        clients = clients == null ? List.of() : List.copyOf(clients);
        projects = projects == null ? List.of() : List.copyOf(projects);
    }

    public record ClientItem(
            String clientCode,
            String clientName,
            String status,
            List<String> brandNames,
            int projectCount
    ) {
        public ClientItem {
            brandNames = brandNames == null ? List.of() : List.copyOf(brandNames);
        }
    }

    public record ProjectItem(
            UUID id,
            String projectCode,
            String projectName,
            String clientCode,
            String clientName,
            LocalDate startsOn,
            LocalDate endsOn,
            List<String> regionNames,
            int networkCount,
            String status,
            Integer publishedConfigurationCount,
            Integer draftConfigurationCount,
            String configurationStatus,
            boolean dataComplete,
            String dataProblem
    ) {
        public ProjectItem {
            regionNames = regionNames == null ? List.of() : List.copyOf(regionNames);
        }
    }
}
