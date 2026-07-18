package com.serviceos.configuration.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationDraftService;
import com.serviceos.configuration.api.ConfigurationDraftView;
import com.serviceos.configuration.api.ConfigurationSimulationOutcome;
import com.serviceos.configuration.api.ConfigurationSimulationReport;
import com.serviceos.configuration.api.ConfigurationSimulationStep;
import com.serviceos.configuration.api.ConfigurationWorkflowSimulationService;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.configuration.api.ExpressionDefinition;
import com.serviceos.configuration.api.ExpressionEvaluationException;
import com.serviceos.configuration.api.ExpressionEvaluator;
import com.serviceos.configuration.api.RunConfigurationSimulationCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.UUID;

/**
 * WORKFLOW 干跑模拟。
 *
 * <p>只读解析定义并求值网关条件；不写 workflow/task 运行时表，不发送外部信号。</p>
 */
@Service
final class DefaultConfigurationWorkflowSimulationService implements ConfigurationWorkflowSimulationService {
    private static final String WRITE = "configuration.draft.write";
    private static final String RESOURCE = "ConfigurationSimulation";
    private static final int DEFAULT_MAX_STEPS = 64;
    private static final Set<String> TASK_TYPES = Set.of(
            "USER_TASK", "SERVICE_TASK", "REVIEW_TASK", "MANUAL_INTERVENTION");

    private final AuthorizationService authorization;
    private final ConfigurationDraftService drafts;
    private final ExpressionEvaluator expressions;
    private final ObjectMapper objectMapper;

    DefaultConfigurationWorkflowSimulationService(
            AuthorizationService authorization,
            ConfigurationDraftService drafts,
            ExpressionEvaluator expressions,
            ObjectMapper objectMapper
    ) {
        this.authorization = authorization;
        this.drafts = drafts;
        this.expressions = expressions;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public ConfigurationSimulationReport simulateDraft(
            CurrentPrincipal principal,
            String correlationId,
            UUID draftId,
            ExpressionContext context,
            Integer maxSteps
    ) {
        ConfigurationDraftView draft = drafts.get(principal, correlationId, draftId);
        ConfigurationSimulationReport report = simulate(principal, correlationId,
                new RunConfigurationSimulationCommand(
                        draft.assetType(), draft.assetKey(), draft.definitionJson(),
                        context, maxSteps));
        return new ConfigurationSimulationReport(
                report.assetType(), report.assetKey(), draftId,
                report.outcome(), report.message(), report.steps());
    }

    @Override
    @Transactional(readOnly = true)
    public ConfigurationSimulationReport simulate(
            CurrentPrincipal principal,
            String correlationId,
            RunConfigurationSimulationCommand command
    ) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(command, "command");
        authorization.require(principal, AuthorizationRequest.tenantCapability(
                WRITE, principal.tenantId(), RESOURCE, command.assetType().name()), correlationId);
        if (command.assetType() != ConfigurationAssetType.WORKFLOW) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "M292 干跑模拟目前仅支持 WORKFLOW");
        }
        String assetKey = blankTo(command.assetKey(), "anonymous-workflow");
        int limit = command.maxSteps() == null || command.maxSteps() < 1
                ? DEFAULT_MAX_STEPS : Math.min(command.maxSteps(), 256);
        Graph graph = parseGraph(requireDefinition(command.definitionJson()));
        List<ConfigurationSimulationStep> steps = new ArrayList<>();
        String current = graph.startNodeId();
        steps.add(step(steps.size(), current, "START", "ENTER", "进入起点"));
        Set<String> visitedGateways = new HashSet<>();
        try {
            current = singleUnconditional(graph, current, "START");
            while (steps.size() < limit) {
                JsonNode node = graph.nodes().get(current);
                if (node == null) {
                    return fail(assetKey, steps, "未知节点: " + current);
                }
                String type = text(node, "nodeType");
                if (TASK_TYPES.contains(type)) {
                    steps.add(step(steps.size(), current, type, "ACTIVATE",
                            "模拟激活任务 " + text(node, "taskType")));
                    steps.add(step(steps.size(), current, type, "COMPLETE", "模拟完成任务"));
                    current = singleUnconditional(graph, current, type);
                    continue;
                }
                if ("EXCLUSIVE_GATEWAY".equals(type)) {
                    if (!visitedGateways.add(current)) {
                        return fail(assetKey, steps, "检测到网关环路: " + current);
                    }
                    steps.add(step(steps.size(), current, type, "EVALUATE", "求值互斥网关"));
                    current = chooseExclusive(graph, current, command.context(), steps);
                    continue;
                }
                if ("WAIT_EVENT".equals(type)) {
                    steps.add(step(steps.size(), current, type, "WAIT",
                            "等待外部事件，干跑不伪造信号"));
                    return new ConfigurationSimulationReport(
                            ConfigurationAssetType.WORKFLOW, assetKey, null,
                            ConfigurationSimulationOutcome.WAITING,
                            "在 WAIT_EVENT 暂停: " + current, List.copyOf(steps));
                }
                if ("TIMER".equals(type) || "PARALLEL_GATEWAY".equals(type)
                        || "SUB_PROCESS".equals(type)) {
                    steps.add(step(steps.size(), current, type, "UNSUPPORTED",
                            "M292 干跑对该节点类型仅记录暂停，不展开执行"));
                    return new ConfigurationSimulationReport(
                            ConfigurationAssetType.WORKFLOW, assetKey, null,
                            ConfigurationSimulationOutcome.WAITING,
                            "在 " + type + " 暂停: " + current, List.copyOf(steps));
                }
                if ("END".equals(type)) {
                    steps.add(step(steps.size(), current, type, "END", "到达结束节点"));
                    return new ConfigurationSimulationReport(
                            ConfigurationAssetType.WORKFLOW, assetKey, null,
                            ConfigurationSimulationOutcome.COMPLETED,
                            "干跑完成", List.copyOf(steps));
                }
                return fail(assetKey, steps, "不支持的节点类型: " + type);
            }
            return new ConfigurationSimulationReport(
                    ConfigurationAssetType.WORKFLOW, assetKey, null,
                    ConfigurationSimulationOutcome.STEP_LIMIT,
                    "达到步数上限 " + limit, List.copyOf(steps));
        } catch (IllegalArgumentException | ExpressionEvaluationException exception) {
            steps.add(step(steps.size(), current, "ERROR", "FAIL_CLOSED", exception.getMessage()));
            return fail(assetKey, steps, exception.getMessage());
        }
    }

    private String chooseExclusive(
            Graph graph,
            String gatewayId,
            ExpressionContext context,
            List<ConfigurationSimulationStep> steps
    ) {
        List<Transition> outgoing = graph.transitions().stream()
                .filter(t -> t.from().equals(gatewayId))
                .sorted(Comparator.comparingInt(Transition::priority).thenComparing(Transition::to))
                .toList();
        if (outgoing.size() < 2) {
            throw new IllegalArgumentException(
                    "EXCLUSIVE_GATEWAY 至少需要两条出边: " + gatewayId);
        }
        List<Transition> hits = new ArrayList<>();
        for (Transition transition : outgoing) {
            if (transition.condition() == null) {
                throw new IllegalArgumentException(
                        "EXCLUSIVE_GATEWAY 出边必须有条件: " + gatewayId);
            }
            boolean matched = expressions.evaluate(transition.condition(), context).result();
            steps.add(step(steps.size(), gatewayId, "EXCLUSIVE_GATEWAY",
                    matched ? "HIT" : "MISS",
                    "to=" + transition.to() + " source=" + transition.condition().source()));
            if (matched) {
                hits.add(transition);
            }
        }
        if (hits.isEmpty()) {
            throw new IllegalArgumentException("EXCLUSIVE_GATEWAY 零命中失败关闭: " + gatewayId);
        }
        if (hits.size() > 1) {
            throw new IllegalArgumentException("EXCLUSIVE_GATEWAY 多命中失败关闭: " + gatewayId);
        }
        return hits.getFirst().to();
    }

    private static String singleUnconditional(Graph graph, String from, String label) {
        List<Transition> outgoing = graph.transitions().stream()
                .filter(t -> t.from().equals(from))
                .toList();
        if (outgoing.size() != 1) {
            throw new IllegalArgumentException(label + " 必须恰好一条出边: " + from);
        }
        if (outgoing.getFirst().condition() != null) {
            throw new IllegalArgumentException(label + " 出边不得带条件: " + from);
        }
        return outgoing.getFirst().to();
    }

    private Graph parseGraph(String definitionJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(definitionJson);
        } catch (JacksonException exception) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "WORKFLOW 定义不是合法 JSON");
        }
        String start = text(root, "startNodeId");
        Map<String, JsonNode> nodes = new HashMap<>();
        for (JsonNode node : root.path("nodes")) {
            nodes.put(text(node, "nodeId"), node);
        }
        List<Transition> transitions = new ArrayList<>();
        for (JsonNode transition : root.path("transitions")) {
            ExpressionDefinition condition = readCondition(transition.get("condition"));
            int priority = transition.has("priority") && !transition.path("priority").isNull()
                    ? transition.path("priority").asInt(100) : 100;
            transitions.add(new Transition(
                    text(transition, "from"), text(transition, "to"), condition, priority));
        }
        return new Graph(start, Map.copyOf(nodes), List.copyOf(transitions));
    }

    private static ExpressionDefinition readCondition(JsonNode condition) {
        if (condition == null || condition.isNull() || !condition.isObject()) {
            return null;
        }
        JsonNode source = condition.get("source");
        if (source == null || !source.isTextual() || source.asText().isBlank()) {
            return null;
        }
        return new ExpressionDefinition(ExpressionDefinition.SERVICEOS_EXPR_V1, source.asText().trim());
    }

    private static ConfigurationSimulationReport fail(
            String assetKey,
            List<ConfigurationSimulationStep> steps,
            String message
    ) {
        return new ConfigurationSimulationReport(
                ConfigurationAssetType.WORKFLOW, assetKey, null,
                ConfigurationSimulationOutcome.FAIL_CLOSED, message, List.copyOf(steps));
    }

    private static ConfigurationSimulationStep step(
            int index, String nodeId, String nodeType, String action, String detail
    ) {
        return new ConfigurationSimulationStep(index, nodeId, nodeType, action, detail);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("缺少字段: " + field);
        }
        return value.asText().trim();
    }

    private static String requireDefinition(String definitionJson) {
        if (definitionJson == null || definitionJson.isBlank()) {
            throw new IllegalArgumentException("definitionJson must not be blank");
        }
        return definitionJson.trim();
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record Graph(String startNodeId, Map<String, JsonNode> nodes, List<Transition> transitions) {
    }

    private record Transition(String from, String to, ExpressionDefinition condition, int priority) {
    }
}
