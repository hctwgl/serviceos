package com.serviceos.configuration.web;

import com.serviceos.configuration.api.ConfigurationSimulationReport;
import com.serviceos.configuration.api.ExpressionContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SimulationHttpModels {
    private SimulationHttpModels() {
    }

    record SimulateRequest(SimulationContextRequest context, Integer maxSteps) {
    }

    record SimulationContextRequest(
            WorkOrderContextRequest workOrder,
            RegionContextRequest region,
            TaskContextRequest task,
            Map<String, Object> formValues
    ) {
    }

    record WorkOrderContextRequest(String clientCode, String brandCode, String serviceProductCode) {
    }

    record RegionContextRequest(String provinceCode, String cityCode, String districtCode) {
    }

    record TaskContextRequest(String stageCode, String taskType) {
    }

    record SimulationReportResponse(
            String assetType,
            String assetKey,
            UUID draftId,
            String outcome,
            String message,
            List<SimulationStepResponse> steps
    ) {
    }

    record SimulationStepResponse(
            int index,
            String nodeId,
            String nodeType,
            String action,
            String detail
    ) {
    }

    static ExpressionContext toContext(SimulationContextRequest request) {
        if (request == null) {
            return new ExpressionContext(
                    new ExpressionContext.WorkOrderContext(null, null, null),
                    new ExpressionContext.RegionContext(null, null, null),
                    new ExpressionContext.TaskContext(null, null),
                    Map.of());
        }
        WorkOrderContextRequest workOrder = request.workOrder() == null
                ? new WorkOrderContextRequest(null, null, null) : request.workOrder();
        RegionContextRequest region = request.region() == null
                ? new RegionContextRequest(null, null, null) : request.region();
        TaskContextRequest task = request.task() == null
                ? new TaskContextRequest(null, null) : request.task();
        return new ExpressionContext(
                new ExpressionContext.WorkOrderContext(
                        workOrder.clientCode(), workOrder.brandCode(), workOrder.serviceProductCode()),
                new ExpressionContext.RegionContext(
                        region.provinceCode(), region.cityCode(), region.districtCode()),
                new ExpressionContext.TaskContext(task.stageCode(), task.taskType()),
                request.formValues() == null ? Map.of() : request.formValues());
    }

    static SimulationReportResponse toResponse(ConfigurationSimulationReport report) {
        return new SimulationReportResponse(
                report.assetType().name(),
                report.assetKey(),
                report.draftId(),
                report.outcome().name(),
                report.message(),
                report.steps().stream()
                        .map(step -> new SimulationStepResponse(
                                step.index(), step.nodeId(), step.nodeType(),
                                step.action(), step.detail()))
                        .toList());
    }
}
