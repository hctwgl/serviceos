package com.serviceos.readmodel.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Network Portal 工单列表项：按 ACTIVE NETWORK 责任聚合。
 *
 * <p>M236：可选服务产品 / 区域 / 接收时间来自工单权威头（非 PII）；工单缺失时为 null。</p>
 * <p>M428：服务端脱敏客户联系随基座返回（不 soft-omit）；原文永不离开 workorder 模块。</p>
 */
public record NetworkPortalWorkOrderItem(
        UUID workOrderId,
        UUID projectId,
        List<UUID> taskIds,
        String businessType,
        String technicianId,
        Instant effectiveFrom,
        String brandCode,
        String serviceProductCode,
        String provinceCode,
        String cityCode,
        String districtCode,
        Instant receivedAt,
        @JsonInclude(JsonInclude.Include.ALWAYS) String maskedCustomerName,
        @JsonInclude(JsonInclude.Include.ALWAYS) String maskedCustomerPhone,
        @JsonInclude(JsonInclude.Include.ALWAYS) String maskedServiceAddress
) {
    public NetworkPortalWorkOrderItem {
        taskIds = taskIds == null ? List.of() : List.copyOf(taskIds);
    }

    /** M194～M235 兼容：无工单头 / 脱敏 enrichment。 */
    public NetworkPortalWorkOrderItem(
            UUID workOrderId,
            UUID projectId,
            List<UUID> taskIds,
            String businessType,
            String technicianId,
            Instant effectiveFrom
    ) {
        this(workOrderId, projectId, taskIds, businessType, technicianId, effectiveFrom,
                null, null, null, null, null, null, null, null, null);
    }
}
