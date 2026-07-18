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
 * <p>M17～M19 线性推进；M269 EXCLUSIVE_GATEWAY；M270 WAIT_EVENT；M275 PARALLEL_GATEWAY
 * 分叉/汇聚；M276 TIMER；M277 SUB_PROCESS。多实例仍未实现。</p>
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
     * <p>已完成任务必须恰好一条无条件出边；目标可为任务、END、EXCLUSIVE_GATEWAY 或 WAIT_EVENT。
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
        return resolveTarget(
                graph, requiredText(firstTarget, "nodeId"), context, new HashSet<>(), requiredNodeId);
    }

    private ProgressionDefinition resolveTarget(
            Graph graph,
            String targetNodeId,
            ExpressionContext context,
            Set<String> visiting,
            String arrivalFromNodeId
    ) {
        if (!visiting.add(targetNodeId)) {
            throw new IllegalArgumentException("workflow gateway cycle detected at " + targetNodeId);
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
                    next.formRef(), next.slaRef(), next.multiInstanceCardinality());
        }
        if ("EXCLUSIVE_GATEWAY".equals(targetType)) {
            String chosen = chooseExclusiveGatewayTarget(graph, targetNodeId, context);
            return resolveTarget(graph, chosen, context, visiting, targetNodeId);
        }
        if ("WAIT_EVENT".equals(targetType)) {
            return ProgressionDefinition.waiting(
                    targetNodeId,
                    requiredText(target, "stageCode"),
                    requiredText(target, "waitEventType"),
                    requiredText(target, "correlationKeyTemplate"));
        }
        if ("TIMER".equals(targetType)) {
            JsonNode duration = target.get("durationSeconds");
            if (duration == null || duration.isNull() || !duration.isIntegralNumber() || duration.asInt() < 1) {
                throw new IllegalArgumentException("TIMER durationSeconds must be positive: " + targetNodeId);
            }
            return ProgressionDefinition.timer(
                    targetNodeId,
                    requiredText(target, "stageCode"),
                    duration.asInt());
        }
        if ("SUB_PROCESS".equals(targetType)) {
            return ProgressionDefinition.subProcess(
                    targetNodeId,
                    requiredText(target, "stageCode"),
                    requiredText(target, "subProcessRef"));
        }
        if ("PARALLEL_GATEWAY".equals(targetType)) {
            return resolveParallelGateway(graph, targetNodeId, context, visiting, arrivalFromNodeId);
        }
        throw new IllegalArgumentException(
                "workflow progression does not support nodeType: " + targetType);
    }

    private ProgressionDefinition resolveParallelGateway(
            Graph graph,
            String gatewayNodeId,
            ExpressionContext context,
            Set<String> visiting,
            String arrivalFromNodeId
    ) {
        List<Transition> outgoing = graph.transitions().stream()
                .filter(transition -> transition.from().equals(gatewayNodeId))
                .toList();
        List<Transition> incoming = graph.transitions().stream()
                .filter(transition -> transition.to().equals(gatewayNodeId))
                .toList();
        boolean fork = outgoing.size() >= 2 && outgoing.stream().allMatch(Transition::unconditional);
        boolean join = incoming.size() >= 2;
        if (fork && join) {
            throw new IllegalArgumentException(
                    "PARALLEL_GATEWAY cannot be both fork and join: " + gatewayNodeId);
        }
        if (fork) {
            List<ProgressionDefinition> branches = new ArrayList<>();
            String sharedStage = null;
            for (Transition transition : outgoing) {
                ProgressionDefinition branch = resolveTarget(
                        graph, transition.to(), context, new HashSet<>(visiting), gatewayNodeId);
                if (branch.end() || branch.joinPending() || branch.fork()) {
                    throw new IllegalArgumentException(
                            "PARALLEL fork branch must be task, WAIT_EVENT or TIMER: " + transition.to());
                }
                if (sharedStage == null) {
                    sharedStage = branch.stageCode();
                } else if (!sharedStage.equals(branch.stageCode())) {
                    throw new IllegalArgumentException(
                            "PARALLEL fork branches must share stageCode: " + gatewayNodeId);
                }
                branches.add(branch);
            }
            return ProgressionDefinition.fork(gatewayNodeId, sharedStage, branches);
        }
        if (join) {
            if (outgoing.size() != 1 || !outgoing.getFirst().unconditional()) {
                throw new IllegalArgumentException(
                        "PARALLEL join must have exactly one unconditional outgoing: " + gatewayNodeId);
            }
            if (arrivalFromNodeId == null || arrivalFromNodeId.isBlank()) {
                throw new IllegalArgumentException(
                        "PARALLEL join requires arrival fromNodeId: " + gatewayNodeId);
            }
            return ProgressionDefinition.joinPending(gatewayNodeId, arrivalFromNodeId, incoming.size());
        }
        throw new IllegalArgumentException(
                "PARALLEL_GATEWAY must be fork (>=2 outs) or join (>=2 ins): " + gatewayNodeId);
    }

    /**
     * 从已唤醒的 WAIT_EVENT 节点推进到其唯一无条件后继。
     */
    ProgressionDefinition progressionAfterWait(
            ConfigurationAssetDefinition asset,
            String waitNodeId,
            ExpressionContext context
    ) {
        Objects.requireNonNull(context, "expression context must not be null");
        Graph graph = parseGraph(asset);
        String requiredNodeId = requiredValue(waitNodeId, "waitNodeId");
        JsonNode waitNode = graph.nodes().get(requiredNodeId);
        if (waitNode == null || !"WAIT_EVENT".equals(requiredText(waitNode, "nodeType"))) {
            throw new IllegalArgumentException("wait node must be WAIT_EVENT: " + requiredNodeId);
        }
        JsonNode target = requireSingleUnconditionalTarget(graph, requiredNodeId, "WAIT_EVENT node");
        return resolveTarget(
                graph, requiredText(target, "nodeId"), context, new HashSet<>(), requiredNodeId);
    }

    /** 子流程完成后，沿父节点唯一出边继续。 */
    ProgressionDefinition progressionAfterSubProcess(
            ConfigurationAssetDefinition asset,
            String subProcessNodeId,
            ExpressionContext context
    ) {
        Objects.requireNonNull(context, "expression context must not be null");
        Graph graph = parseGraph(asset);
        String requiredNodeId = requiredValue(subProcessNodeId, "subProcessNodeId");
        JsonNode sub = graph.nodes().get(requiredNodeId);
        if (sub == null || !"SUB_PROCESS".equals(requiredText(sub, "nodeType"))) {
            throw new IllegalArgumentException("node must be SUB_PROCESS: " + requiredNodeId);
        }
        JsonNode target = requireSingleUnconditionalTarget(graph, requiredNodeId, "SUB_PROCESS node");
        return resolveTarget(
                graph, requiredText(target, "nodeId"), context, new HashSet<>(), requiredNodeId);
    }

    /** TIMER 到期后，沿唯一出边继续。 */
    ProgressionDefinition progressionAfterTimer(
            ConfigurationAssetDefinition asset,
            String timerNodeId,
            ExpressionContext context
    ) {
        Objects.requireNonNull(context, "expression context must not be null");
        Graph graph = parseGraph(asset);
        String requiredNodeId = requiredValue(timerNodeId, "timerNodeId");
        JsonNode timerNode = graph.nodes().get(requiredNodeId);
        if (timerNode == null || !"TIMER".equals(requiredText(timerNode, "nodeType"))) {
            throw new IllegalArgumentException("timer node must be TIMER: " + requiredNodeId);
        }
        JsonNode target = requireSingleUnconditionalTarget(graph, requiredNodeId, "TIMER node");
        return resolveTarget(
                graph, requiredText(target, "nodeId"), context, new HashSet<>(), requiredNodeId);
    }

    /** Join 汇聚完成后，沿唯一出边继续。 */
    ProgressionDefinition progressionAfterJoin(
            ConfigurationAssetDefinition asset,
            String joinNodeId,
            ExpressionContext context
    ) {
        Objects.requireNonNull(context, "expression context must not be null");
        Graph graph = parseGraph(asset);
        String requiredNodeId = requiredValue(joinNodeId, "joinNodeId");
        JsonNode joinNode = graph.nodes().get(requiredNodeId);
        if (joinNode == null || !"PARALLEL_GATEWAY".equals(requiredText(joinNode, "nodeType"))) {
            throw new IllegalArgumentException("join node must be PARALLEL_GATEWAY: " + requiredNodeId);
        }
        JsonNode target = requireSingleUnconditionalTarget(graph, requiredNodeId, "PARALLEL join");
        return resolveTarget(
                graph, requiredText(target, "nodeId"), context, new HashSet<>(), requiredNodeId);
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
                optionalText(node, "formRef"), optionalText(node, "slaRef"),
                readMultiInstanceCardinality(node));
    }

    private static int readMultiInstanceCardinality(JsonNode node) {
        JsonNode multi = node.get("multiInstance");
        if (multi == null || multi.isNull()) {
            return 1;
        }
        JsonNode cardinality = multi.get("cardinality");
        if (cardinality == null || !cardinality.isIntegralNumber()
                || cardinality.asInt() < 2 || cardinality.asInt() > 50) {
            throw new IllegalArgumentException(
                    "multiInstance.cardinality must be between 2 and 50");
        }
        return cardinality.asInt();
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
            boolean end,
            boolean waiting,
            String waitEventType,
            String correlationKeyTemplate,
            boolean timer,
            int durationSeconds,
            boolean subProcess,
            String subProcessRef,
            boolean fork,
            List<ProgressionDefinition> forkBranches,
            boolean joinPending,
            String joinFromNodeId,
            int expectedJoinTokens,
            int multiInstanceCardinality
    ) {
        boolean multiInstance() {
            return multiInstanceCardinality >= 2;
        }

        static ProgressionDefinition task(
                String nodeId, String stageCode, String taskType, WorkflowTaskKind taskKind,
                String formRef, String slaRef, int multiInstanceCardinality) {
            return new ProgressionDefinition(
                    nodeId, stageCode, taskType, taskKind, formRef, slaRef,
                    false, false, null, null, false, 0, false, null,
                    false, List.of(), false, null, 0, multiInstanceCardinality);
        }

        static ProgressionDefinition end(String nodeId) {
            return new ProgressionDefinition(
                    nodeId, null, null, null, null, null,
                    true, false, null, null, false, 0, false, null,
                    false, List.of(), false, null, 0, 1);
        }

        static ProgressionDefinition waiting(
                String nodeId,
                String stageCode,
                String waitEventType,
                String correlationKeyTemplate
        ) {
            return new ProgressionDefinition(
                    nodeId, stageCode, null, null, null, null,
                    false, true, waitEventType, correlationKeyTemplate,
                    false, 0, false, null, false, List.of(), false, null, 0, 1);
        }

        static ProgressionDefinition timer(String nodeId, String stageCode, int durationSeconds) {
            return new ProgressionDefinition(
                    nodeId, stageCode, null, null, null, null,
                    false, false, null, null, true, durationSeconds, false, null,
                    false, List.of(), false, null, 0, 1);
        }

        static ProgressionDefinition subProcess(String nodeId, String stageCode, String subProcessRef) {
            return new ProgressionDefinition(
                    nodeId, stageCode, null, null, null, null,
                    false, false, null, null, false, 0, true, subProcessRef,
                    false, List.of(), false, null, 0, 1);
        }

        static ProgressionDefinition fork(
                String forkNodeId,
                String stageCode,
                List<ProgressionDefinition> branches
        ) {
            return new ProgressionDefinition(
                    forkNodeId, stageCode, null, null, null, null,
                    false, false, null, null, false, 0, false, null,
                    true, List.copyOf(branches), false, null, 0, 1);
        }

        static ProgressionDefinition joinPending(
                String joinNodeId,
                String fromNodeId,
                int expectedTokens
        ) {
            return new ProgressionDefinition(
                    joinNodeId, null, null, null, null, null,
                    false, false, null, null, false, 0, false, null,
                    false, List.of(), true, fromNodeId, expectedTokens, 1);
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
            String slaRef,
            int multiInstanceCardinality
    ) {
    }
}
