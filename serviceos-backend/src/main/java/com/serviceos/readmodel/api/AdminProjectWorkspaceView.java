package com.serviceos.readmodel.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Admin 项目详情及履约配置概览页面级读模型。 */
public record AdminProjectWorkspaceView(
        UUID projectId,
        String projectCode,
        String projectName,
        String clientName,
        String status,
        LocalDate startsOn,
        LocalDate endsOn,
        List<String> regionNames,
        List<String> networkNames,
        boolean configurationReadable,
        List<FulfillmentProfile> fulfillmentProfiles,
        Integer activeWorkOrderCount,
        Boolean activeWorkOrderCountTruncated,
        boolean dataComplete,
        String dataProblem,
        Instant asOf
) {
    public AdminProjectWorkspaceView {
        regionNames = regionNames == null ? List.of() : List.copyOf(regionNames);
        networkNames = networkNames == null ? List.of() : List.copyOf(networkNames);
        fulfillmentProfiles = fulfillmentProfiles == null ? List.of() : List.copyOf(fulfillmentProfiles);
    }

    public record FulfillmentProfile(
            UUID profileId,
            String profileName,
            String serviceProductName,
            String status,
            Integer stageCount,
            Integer formCount,
            Integer evidenceCount,
            String activeVersion,
            Instant effectiveFrom,
            String workflowSummary,
            String slaSummary,
            Instant updatedAt,
            boolean dataComplete,
            String dataProblem
    ) {
    }
}
