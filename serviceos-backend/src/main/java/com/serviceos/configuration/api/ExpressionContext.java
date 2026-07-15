package com.serviceos.configuration.api;

/** ADR-018 M52：表达式求值显式上下文；仅白名单字段可被引用。 */
public record ExpressionContext(
        WorkOrderContext workOrder,
        RegionContext region,
        TaskContext task
) {
    public record WorkOrderContext(String clientCode, String brandCode, String serviceProductCode) {
    }

    public record RegionContext(String provinceCode, String cityCode, String districtCode) {
    }

    public record TaskContext(String stageCode, String taskType) {
    }
}
