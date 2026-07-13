package com.serviceos.workflow.application;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.task.api.WorkflowTaskKind;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 受控 JSON 工作流的最小可执行解释器。M17 只允许 START 后紧接一个任务节点，其他图形先拒绝而不猜测。
 */
@Component
final class WorkflowDefinitionParser {
    private static final Set<String> TASK_NODE_TYPES = Set.of(
            "USER_TASK", "SERVICE_TASK", "REVIEW_TASK", "MANUAL_INTERVENTION");

    private final ObjectMapper objectMapper;

    WorkflowDefinitionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    BootstrapDefinition parse(ConfigurationAssetDefinition asset) {
        final JsonNode root;
        try {
            root = objectMapper.readTree(asset.definitionJson());
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("workflow definition is not valid JSON", exception);
        }
        String workflowKey = requiredText(root, "workflowKey");
        String semanticVersion = requiredText(root, "semanticVersion");
        if (!semanticVersion.equals(asset.semanticVersion())) {
            throw new IllegalArgumentException("workflow semanticVersion does not match frozen asset version");
        }
        String startNodeId = requiredText(root, "startNodeId");
        JsonNode nodesNode = root.get("nodes");
        JsonNode transitionsNode = root.get("transitions");
        if (nodesNode == null || !nodesNode.isArray() || transitionsNode == null || !transitionsNode.isArray()) {
            throw new IllegalArgumentException("workflow nodes and transitions must be arrays");
        }

        Map<String, JsonNode> nodes = new HashMap<>();
        for (JsonNode node : nodesNode) {
            String nodeId = requiredText(node, "nodeId");
            if (nodes.putIfAbsent(nodeId, node) != null) {
                throw new IllegalArgumentException("workflow nodeId must be unique: " + nodeId);
            }
        }
        JsonNode start = nodes.get(startNodeId);
        if (start == null || !"START".equals(requiredText(start, "nodeType"))) {
            throw new IllegalArgumentException("startNodeId must reference a START node");
        }

        String startTargetId = null;
        int unconditionalStartTransitions = 0;
        for (JsonNode transition : transitionsNode) {
            String from = requiredText(transition, "from");
            String to = requiredText(transition, "to");
            if (!nodes.containsKey(from) || !nodes.containsKey(to)) {
                throw new IllegalArgumentException("workflow transition references an unknown node");
            }
            JsonNode condition = transition.get("condition");
            if (startNodeId.equals(from) && (condition == null || condition.isNull()
                    || condition.asText().isBlank())) {
                unconditionalStartTransitions++;
                startTargetId = to;
            }
        }
        if (unconditionalStartTransitions != 1) {
            throw new IllegalArgumentException("START must have exactly one unconditional outgoing transition");
        }
        JsonNode first = nodes.get(startTargetId);
        String nodeType = requiredText(first, "nodeType");
        if (!TASK_NODE_TYPES.contains(nodeType)) {
            throw new IllegalArgumentException("the first executable node must be a task node");
        }
        return new BootstrapDefinition(
                workflowKey, semanticVersion, requiredText(first, "nodeId"),
                requiredText(first, "stageCode"), requiredText(first, "taskType"),
                "SERVICE_TASK".equals(nodeType) ? WorkflowTaskKind.AUTOMATED : WorkflowTaskKind.HUMAN);
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("workflow " + field + " must not be blank");
        }
        return value.asText().trim();
    }

    record BootstrapDefinition(
            String workflowKey,
            String workflowVersion,
            String firstNodeId,
            String firstStageCode,
            String firstTaskType,
            WorkflowTaskKind firstTaskKind
    ) {
    }
}
