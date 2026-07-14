package com.serviceos.workflow.application;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.task.api.WorkflowTaskKind;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 受控 JSON 工作流的最小可执行解释器。
 *
 * <p>M17 负责 START 到首任务，M18 增加任务到下一任务的线性推进。条件网关、并行、
 * 跨阶段和 END 仍然失败关闭，绝不把尚未实现的语义猜成顺序流。</p>
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
        Graph graph = parseGraph(asset);
        JsonNode start = graph.nodes().get(graph.startNodeId());
        if (start == null || !"START".equals(requiredText(start, "nodeType"))) {
            throw new IllegalArgumentException("startNodeId must reference a START node");
        }
        JsonNode first = requireSingleUnconditionalTarget(graph, graph.startNodeId(), "START");
        TaskNode task = requireTaskNode(first, "the first executable node");
        return new BootstrapDefinition(
                graph.workflowKey(), graph.semanticVersion(), task.nodeId(),
                task.stageCode(), task.taskType(), task.taskKind());
    }

    NextTaskDefinition nextTask(ConfigurationAssetDefinition asset, String completedNodeId) {
        Graph graph = parseGraph(asset);
        String requiredNodeId = requiredValue(completedNodeId, "completedNodeId");
        JsonNode completed = graph.nodes().get(requiredNodeId);
        if (completed == null) {
            throw new IllegalArgumentException("completed workflow node does not exist: " + requiredNodeId);
        }
        requireTaskNode(completed, "completed workflow node");

        JsonNode target = requireSingleUnconditionalTarget(graph, requiredNodeId, "completed task node");
        String targetType = requiredText(target, "nodeType");
        if ("END".equals(targetType)) {
            throw new IllegalArgumentException("END progression is not supported by M18");
        }
        TaskNode next = requireTaskNode(target, "next executable node");
        return new NextTaskDefinition(next.nodeId(), next.stageCode(), next.taskType(), next.taskKind());
    }

    private Graph parseGraph(ConfigurationAssetDefinition asset) {
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
        List<Transition> transitions = new ArrayList<>();
        for (JsonNode transition : transitionsNode) {
            String from = requiredText(transition, "from");
            String to = requiredText(transition, "to");
            if (!nodes.containsKey(from) || !nodes.containsKey(to)) {
                throw new IllegalArgumentException("workflow transition references an unknown node");
            }
            transitions.add(new Transition(from, to, isUnconditional(transition.get("condition"))));
        }
        return new Graph(workflowKey, semanticVersion, startNodeId, Map.copyOf(nodes), List.copyOf(transitions));
    }

    private static JsonNode requireSingleUnconditionalTarget(Graph graph, String fromNodeId, String label) {
        List<Transition> outgoing = graph.transitions().stream()
                .filter(transition -> transition.from().equals(fromNodeId))
                .toList();
        if (outgoing.size() != 1 || !outgoing.getFirst().unconditional()) {
            throw new IllegalArgumentException(
                    label + " must have exactly one unconditional outgoing transition");
        }
        return graph.nodes().get(outgoing.getFirst().to());
    }

    private static TaskNode requireTaskNode(JsonNode node, String label) {
        String nodeType = requiredText(node, "nodeType");
        if (!TASK_NODE_TYPES.contains(nodeType)) {
            throw new IllegalArgumentException(label + " must be a task node");
        }
        return new TaskNode(
                requiredText(node, "nodeId"), requiredText(node, "stageCode"),
                requiredText(node, "taskType"),
                "SERVICE_TASK".equals(nodeType) ? WorkflowTaskKind.AUTOMATED : WorkflowTaskKind.HUMAN);
    }

    private static boolean isUnconditional(JsonNode condition) {
        return condition == null || condition.isNull()
                || (condition.isTextual() && condition.asText().isBlank());
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("workflow " + field + " must not be blank");
        }
        return value.asText().trim();
    }

    private static String requiredValue(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("workflow " + field + " must not be blank");
        }
        return value.trim();
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

    record NextTaskDefinition(
            String nodeId,
            String stageCode,
            String taskType,
            WorkflowTaskKind taskKind
    ) {
    }

    private record Graph(
            String workflowKey,
            String semanticVersion,
            String startNodeId,
            Map<String, JsonNode> nodes,
            List<Transition> transitions
    ) {
    }

    private record Transition(String from, String to, boolean unconditional) {
    }

    private record TaskNode(
            String nodeId,
            String stageCode,
            String taskType,
            WorkflowTaskKind taskKind
    ) {
    }
}
