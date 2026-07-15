package com.serviceos.configuration.api;

import java.util.Map;

/** ADR-018：表达式求值显式上下文；仅静态白名单与声明过的表单字段可被引用。 */
public record ExpressionContext(
        WorkOrderContext workOrder,
        RegionContext region,
        TaskContext task,
        Map<String, Object> formValues
) {
    public ExpressionContext {
        formValues = formValues == null ? Map.of() : Map.copyOf(formValues);
    }

    /** M52 固定上下文兼容构造；不存在表单事实时必须显式使用空映射。 */
    public ExpressionContext(
            WorkOrderContext workOrder,
            RegionContext region,
            TaskContext task
    ) {
        this(workOrder, region, task, Map.of());
    }

    public ExpressionContext withFormValues(Map<String, Object> values) {
        return new ExpressionContext(workOrder, region, task, values);
    }

    public record WorkOrderContext(String clientCode, String brandCode, String serviceProductCode) {
    }

    public record RegionContext(String provinceCode, String cityCode, String districtCode) {
    }

    public record TaskContext(String stageCode, String taskType) {
    }
}
