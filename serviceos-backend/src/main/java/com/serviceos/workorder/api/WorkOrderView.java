package com.serviceos.workorder.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * WorkOrder 运营概要。
 *
 * <p>不含客户原文 PII。M429：可含服务端脱敏客户联系（masked*）；原文永不离开 workorder 模块。
 * M432：可含当前阶段码（currentStageCode），由 task 旁载，无 ACTIVE 任务时为 null。
 * M449：可含当前任务类型（currentTaskType），与阶段同口径旁载。
 * M446：可含当前任务状态（currentTaskStatus），与阶段同口径旁载。
 * M433：可含当前认领主体与 Persona 显示名；无认领或无档案时为 null，不发明名称。
 * M435：含独立 updatedAt（聚合写路径时间），与 receivedAt 分离。
 * M439：可含 ACTIVE 网点/师傅服务责任及显示名；无 ACTIVE 责任时为 null。</p>
 */
public record WorkOrderView(
        UUID id, String tenantId, UUID projectId, String clientCode, String brandCode,
        String serviceProductCode, String externalOrderCode, String status,
        UUID configurationBundleId, String configurationBundleCode,
        String configurationBundleVersion, String configurationBundleDigest,
        String provinceCode, String cityCode, String districtCode,
        Instant externalDispatchedAt, Instant receivedAt, Instant updatedAt,
        Instant activatedAt, Instant fulfilledAt, long version,
        @JsonInclude(JsonInclude.Include.ALWAYS) String maskedCustomerName,
        @JsonInclude(JsonInclude.Include.ALWAYS) String maskedCustomerPhone,
        @JsonInclude(JsonInclude.Include.ALWAYS) String maskedServiceAddress,
        @JsonInclude(JsonInclude.Include.ALWAYS) String currentStageCode,
        @JsonInclude(JsonInclude.Include.ALWAYS) String currentTaskType,
        @JsonInclude(JsonInclude.Include.ALWAYS) String currentTaskStatus,
        @JsonInclude(JsonInclude.Include.ALWAYS) String currentClaimedBy,
        @JsonInclude(JsonInclude.Include.ALWAYS) String currentAssigneeDisplayName,
        @JsonInclude(JsonInclude.Include.ALWAYS) String currentNetworkId,
        @JsonInclude(JsonInclude.Include.ALWAYS) String currentNetworkDisplayName,
        @JsonInclude(JsonInclude.Include.ALWAYS) String currentTechnicianId,
        @JsonInclude(JsonInclude.Include.ALWAYS) String currentTechnicianDisplayName
) {
    /** 仓储层装载：尚无脱敏 / 阶段 / 任务类型 / 责任人 / 网点师傅 enrichment。 */
    public WorkOrderView(
            UUID id, String tenantId, UUID projectId, String clientCode, String brandCode,
            String serviceProductCode, String externalOrderCode, String status,
            UUID configurationBundleId, String configurationBundleCode,
            String configurationBundleVersion, String configurationBundleDigest,
            String provinceCode, String cityCode, String districtCode,
            Instant externalDispatchedAt, Instant receivedAt, Instant updatedAt,
            Instant activatedAt, Instant fulfilledAt, long version
    ) {
        this(id, tenantId, projectId, clientCode, brandCode, serviceProductCode, externalOrderCode,
                status, configurationBundleId, configurationBundleCode, configurationBundleVersion,
                configurationBundleDigest, provinceCode, cityCode, districtCode,
                externalDispatchedAt, receivedAt, updatedAt, activatedAt, fulfilledAt, version,
                null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
