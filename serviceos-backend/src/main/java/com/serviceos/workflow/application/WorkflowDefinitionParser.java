package com.serviceos.workflow.application;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.configuration.api.ExpressionDefinition;
import com.serviceos.configuration.api.ExpressionEvaluator;
import com.serviceos.task.api.WorkflowTaskKind;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 受控 JSON 工作流的最小可执行解释器。
 *
 * <p>M17～M19 提供线性无条件推进；M269 增加 {@code EXCLUSIVE_GATEWAY}：按出边条件求值，
 * 要求恰好一条为 true，零命中/多命中失败关闭。并行网关、等待事件与子流程仍未实现。</p>
 */
@Component
final class WorkflowDefinitionParser {
    private static final Set<String> TASK_NODE_TYPES = Set.of(
            "USER_TASK", "SERVICE_TASK", "REVIEW_TASK", "MANUAL_INTERVENTION");

    private final ObjectMapper objectMapper;
    private final ExpressionEvaluator expressions;

    WorkflowDefinitionParser(ObjectMapper objectMapper, ExpressionEvaluator expressions) {
        this.objectMapper = objectMapper;
        this.expressions = expressions;
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
                task.stageCode(), task.taskType(), task.taskKind(), task.formRef(), task.slaRef());
    }

    /**
     * 从已完成任务推进到下一可执行任务或 END。
     *
     * <p>已完成任务必须恰好一条无条件出边；目标可为任务、END 或 EXCLUSIVE_GATEWAY。
     * 网关求值需要工单冻结表达式上下文，不得读取“最新配置”。</p>
     */
    ProgressionDefinition progression(
            ConfigurationAssetDefinition asset,
            String completedNodeId,
            ExpressionContext context
    ) {
        Objects.requireNonNull(context, "expression context must not be null");
        Graph graph = parseGraph(asset);
        String requiredNodeId = requiredValue(completedNodeId, "completedNodeId");
        JsonNode completed = graph.nodes().get(requiredNodeId);
        if (completed == null) {
            throw new IllegalArgumentException("completed workflow node does not exist: " + requiredNodeId);
        }
        requireTaskNode(completed, "completed workflow node");
        JsonNode firstTarget = requireSingleUnconditionalTarget(graph, requiredNodeId, "completed task node");
        return resolveTarget(graph, requiredText(firstTarget, "nodeId"), context, new HashSet<>());
    }

    private ProgressionDefinition resolveTarget(
            Graph graph,
            String targetNodeId,
            ExpressionContext context,
            Set<String> visiting
    ) {
        if (!visiting.add(targetNodeId)) {
            throw new IllegalArgumentException("workflow exclusive gateway cycle detected at " + targetNodeId);
        }
        JsonNode target = graph.nodes().get(targetNodeId);
        if (target == null) {
            throw new IllegalArgumentException("workflow target node does not exist: " + targetNodeId);
        }
        String targetType = requiredText(target, "nodeType");
        if ("END".equals(targetType)) {
            return ProgressionDefinition.end(targetNodeId);
        }
        if (TASK_NODE_TYPES.contains(targetType)) {
            TaskNode next = requireTaskNode(target, "next executable node");
            return ProgressionDefinition.task(
                    next.nodeId(), next.stageCode(), next.taskType(), next.taskKind(),
                    next.formRef(), next.slaRef());
        }
        if ("EXCLUSIVE_GATEWAY".equals(targetType)) {
            String chosen = chooseExclusiveGatewayTarget(graph, targetNodeId, context);
            return resolveTarget(graph, chosen, context, visiting);
        }
        throw new IllegalArgumentException(
                "workflow progression does not support nodeType: " + targetType);
    }

    private String chooseExclusiveGatewayTarget(
            Graph graph,
            String gatewayNodeId,
            ExpressionContext context
    ) {
        List<Transition> outgoing = graph.transitions().stream()
                .filter(transition -> transition.from().equals(gatewayNodeId))
                .sorted(Comparator.comparingInt(Transition::priority).thenComparing(Transition::to))
                .toList();
        if (outgoing.size() < 2) {
            throw new IllegalArgumentException(
                    "EXCLUSIVE_GATEWAY must have at least two outgoing transitions: " + gatewayNodeId);
        }
        List<Transition> hits = new ArrayList<>();
        for (Transition transition : outgoing) {
            if (transition.condition() == null) {
                throw new IllegalArgumentException(
                        "EXCLUSIVE_GATEWAY outgoing transition must have a condition: " + gatewayNodeId);
            }
            boolean matched = expressions.evaluate(transition.condition(), context).result();
            if (matched) {
                hits.add(transition);
            }
        }
        if (hits.isEmpty()) {
            throw new IllegalArgumentException(
                    "EXCLUSIVE_GATEWAY zero-hit fail-closed: " + gatewayNodeId);
        }
        if (hits.size() > 1) {
            throw new IllegalArgumentException(
                    "EXCLUSIVE_GATEWAY multi-hit fail-closed: " + gatewayNodeId
                            + " hits=" + hits.stream().map(Transition::to).toList());
        }
        return hits.getFirst().to();
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
            ExpressionDefinition condition = readCondition(transition.get("condition"));
            int priority = transition.has("priority") && !transition.path("priority").isNull()
                    ? transition.path("priority").asInt(100) : 100;
            transitions.add(new Transition(from, to, condition, priority));
        }
        return new Graph(workflowKey, semanticVersion, startNodeId, Map.copyOf(nodes), List.copyOf(transitions));
    }

    private static ExpressionDefinition readCondition(JsonNode condition) {
        if (condition == null || condition.isNull()) {
            return null;
        }
        if (!condition.isObject()) {
            // 裸字符串等非法形状视为“有条件但不可执行”，迫使线性推进失败关闭。
            return new ExpressionDefinition(ExpressionDefinition.SERVICEOS_EXPR_V1, "__invalid_condition__");
        }
        JsonNode source = condition.get("source");
        if (source == null || source.isNull() || (source.isTextual() && source.asText().isBlank())) {
            return null;
        }
        return new ExpressionDefinition(
                condition.path("language").asText(), source.asText().trim());
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
                "SERVICE_TASK".equals(nodeType) ? WorkflowTaskKind.AUTOMATED : WorkflowTaskKind.HUMAN,
                optionalText(node, "formRef"), optionalText(node, "slaRef"));
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

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("workflow " + field + " must be null or non-blank text");
        }
        return value.asText().trim();
    }

    record BootstrapDefinition(
            String workflowKey,
            String workflowVersion,
            String firstNodeId,
            String firstStageCode,
            String firstTaskType,
            WorkflowTaskKind firstTaskKind,
            String firstFormRef,
            String firstSlaRef
    ) {
    }

    record ProgressionDefinition(
            String nodeId,
            String stageCode,
            String taskType,
            WorkflowTaskKind taskKind,
            String formRef,
            String slaRef,
            boolean end
    ) {
        static ProgressionDefinition task(
                String nodeId, String stageCode, String taskType, WorkflowTaskKind taskKind,
                String formRef, String slaRef) {
            return new ProgressionDefinition(nodeId, stageCode, taskType, taskKind, formRef, slaRef, false);
        }

        static ProgressionDefinition end(String nodeId) {
            return new ProgressionDefinition(nodeId, null, null, null, null, null, true);
        }
    }

    private record Graph(
            String workflowKey,
            String semanticVersion,
            String startNodeId,
            Map<String, JsonNode> nodes,
            List<Transition> transitions
    ) {
    }

    private record Transition(
            String from,
            String to,
            ExpressionDefinition condition,
            int priority
    ) {
        boolean unconditional() {
            return condition == null;
        }
    }

    private record TaskNode(
            String nodeId,
            String stageCode,
            String taskType,
            WorkflowTaskKind taskKind,
            String formRef,
            String slaRef
    ) {
    }
}
