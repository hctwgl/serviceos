package com.serviceos.workflow.application;

import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.workflow.api.WorkflowExecutionProjection;
import com.serviceos.workflow.api.WorkflowExecutionQueryService;
import com.serviceos.workflow.api.WorkflowInstanceView;
import com.serviceos.workorder.api.WorkOrderQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.UUID;

/** 先复用 WorkOrder 公开查询完成隔离与鉴权，再读取本模块执行事实。 */
@Service
final class DefaultWorkflowExecutionQueryService implements WorkflowExecutionQueryService {
    private final WorkOrderQueryService workOrders; private final WorkflowExecutionQueryRepository queries;
    private final ConfigurationService configurations;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    DefaultWorkflowExecutionQueryService(WorkOrderQueryService workOrders,
            WorkflowExecutionQueryRepository queries,
            ConfigurationService configurations,
            ObjectMapper objectMapper,
            Clock clock) {
        this.workOrders=workOrders; this.queries=queries; this.configurations=configurations;
        this.objectMapper=objectMapper; this.clock=clock;
    }
    @Override @Transactional(readOnly=true)
    public WorkflowExecutionProjection get(CurrentPrincipal principal,String correlationId,UUID workOrderId) {
        workOrders.get(principal,correlationId,workOrderId);
        WorkflowInstanceView workflow = queries.findWorkflow(principal.tenantId(), workOrderId)
                .map(value -> enrich(principal.tenantId(), value))
                .orElse(null);
        return new WorkflowExecutionProjection(workflow,
                queries.findStages(principal.tenantId(),workOrderId),clock.instant());
    }

    /**
     * 名称和责任必须从工单冻结的 Workflow 版本读取，不能由当前模板或静态枚举推导。
     * 这样发布后重命名草稿不会改变历史工单，同时 Phase 仍只承担展示与统计职责。
     */
    private WorkflowInstanceView enrich(String tenantId, WorkflowInstanceView workflow) {
        var asset = configurations.requireAssetVersion(
                tenantId,
                workflow.workflowDefinitionVersionId(),
                ConfigurationAssetType.WORKFLOW,
                workflow.definitionDigest());
        try {
            JsonNode definition = objectMapper.readTree(asset.definitionJson());
            JsonNode currentNode = findNode(definition.path("nodes"), workflow.currentNodeCode());
            String phaseName = phaseName(definition.path("metadata").path("phaseNames"),
                    workflow.currentPhaseCode());
            return new WorkflowInstanceView(
                    workflow.id(), workflow.projectId(), workflow.workOrderId(),
                    workflow.configurationBundleId(), workflow.workflowDefinitionVersionId(),
                    workflow.workflowKey(), workflow.workflowVersion(), workflow.definitionDigest(),
                    workflow.status(), workflow.currentPhaseCode(), phaseName,
                    workflow.currentNodeCode(), text(currentNode, "name"),
                    text(currentNode, "responsibilityRole"),
                    workflow.version(), workflow.startedAt(), workflow.completedAt());
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "工单冻结流程版本无法生成运营投影: " + workflow.workflowDefinitionVersionId(),
                    exception);
        }
    }

    private static JsonNode findNode(JsonNode nodes, String nodeCode) {
        if (nodeCode == null || !nodes.isArray()) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (nodeCode.equals(node.path("nodeId").asText())) {
                return node;
            }
        }
        return null;
    }

    private static String phaseName(JsonNode phaseNames, String phaseCode) {
        if (phaseCode == null || !phaseNames.isObject()) {
            return null;
        }
        JsonNode value = phaseNames.get(phaseCode);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
