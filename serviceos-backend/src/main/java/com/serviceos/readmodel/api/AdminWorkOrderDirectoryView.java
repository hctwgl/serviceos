package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Admin 工单中心一次装载所需的稳定页面读模型。 */
public record AdminWorkOrderDirectoryView(
        List<Item> items,
        List<ProjectOption> projectOptions,
        AdminWorkbenchView queueSummary,
        String nextCursor,
        int totalCount,
        Instant generatedAt
) {
    public AdminWorkOrderDirectoryView {
        items = List.copyOf(items);
        projectOptions = List.copyOf(projectOptions);
    }

    public record Item(
            UUID id,
            String orderCode,
            String customerName,
            String customerPhone,
            UUID projectId,
            String projectName,
            String clientName,
            String serviceName,
            String stageName,
            String networkName,
            String technicianName,
            String slaLevel,
            String slaLabel,
            String statusName,
            Instant updatedAt,
            boolean dataComplete,
            String dataProblem
    ) {
    }

    public record ProjectOption(UUID id, String name) {
    }
}
