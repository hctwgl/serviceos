package com.serviceos.workorder.api;

import java.time.Instant;
import java.util.UUID;

/**
 * M236：Network Portal 目录用非 PII 工单头（服务产品 / 区域 / 接收时间）。
 * 字段对齐 Admin {@link WorkOrderView} 子集；不含客户地址/车辆/联系方式。
 */
public record WorkOrderDirectoryHeader(
        UUID workOrderId,
        String brandCode,
        String serviceProductCode,
        String provinceCode,
        String cityCode,
        String districtCode,
        Instant receivedAt
) {
}
