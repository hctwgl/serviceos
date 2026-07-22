package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Admin 师傅目录项，聚合档案、服务网点关系和有效资质摘要。 */
public record AdminTechnicianDirectoryItem(
        UUID id,
        String displayName,
        String status,
        List<String> supportedClientKinds,
        List<String> networkNames,
        List<String> approvedQualificationCodes,
        int pendingQualificationCount,
        Instant updatedAt
) {
    public AdminTechnicianDirectoryItem {
        supportedClientKinds = supportedClientKinds == null ? List.of() : List.copyOf(supportedClientKinds);
        networkNames = networkNames == null ? List.of() : List.copyOf(networkNames);
        approvedQualificationCodes = approvedQualificationCodes == null
                ? List.of() : List.copyOf(approvedQualificationCodes);
    }
}
