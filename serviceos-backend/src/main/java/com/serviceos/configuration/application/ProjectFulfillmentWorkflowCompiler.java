package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ProjectFulfillmentDocument;
import com.serviceos.configuration.api.ProjectFulfillmentNodeDraft;
import com.serviceos.configuration.api.ProjectFulfillmentTransitionDraft;
import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Component;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 把产品草稿图编译为既有 Workflow Engine 可执行的严格串行定义。
 *
 * <p>Phase 只被复制到节点 {@code stageCode} 供展示和统计；流转只来自 Node 与
 * Transition。结果分支编译为隐藏的互斥网关，绝不生成并行网关。</p>
 */
@Component
final class ProjectFulfillmentWorkflowCompiler {
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    CompiledWorkflow compile(
            String profileCode,
            String profileName,
            String semanticVersion,
            ProjectFulfillmentDocument document
    ) {
        return compile(profileCode, profileName, semanticVersion, document,
                RuntimeAssetBindings.empty());
    }

    CompiledWorkflow compile(
            String profileCode,
            String profileName,
            String semanticVersion,
            ProjectFulfillmentDocument document,
            RuntimeAssetBindings bindings
    ) {
        if (document.nodes().isEmpty()) {
            throw new IllegalArgumentException("新履约版本必须包含可执行节点图");
        }
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (ProjectFulfillmentNodeDraft node : document.nodes()) {
            nodes.add(runtimeNode(node, bindings));
        }
        CompiledGraph graph = compileTransitions(document);
        nodes.addAll(graph.hiddenGateways());
        String startNodeId = document.nodes().stream()
                .filter(node -> "START".equals(node.nodeType()))
                .map(ProjectFulfillmentNodeDraft::nodeId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("流程缺少开始节点"));
        List<String> terminalNodeIds = document.nodes().stream()
                .filter(node -> "END".equals(node.nodeType()))
                .map(ProjectFulfillmentNodeDraft::nodeId)
                .toList();

        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("workflowKey", normalizeKey(profileCode));
        definition.put("semanticVersion", semanticVersion);
        definition.put("name", profileName);
        definition.put("executionMode", "SERIAL_V1");
        definition.put("startNodeId", startNodeId);
        definition.put("terminalNodeIds", terminalNodeIds);
        definition.put("nodes", nodes);
        definition.put("transitions", graph.transitions());
        definition.put("variables", List.of());
        definition.put("metadata", Map.of(
                "designerSchemaVersion", document.schemaVersion(),
                "layoutDirection", "TOP_TO_BOTTOM",
                "phaseNames", document.phases().stream().collect(
                        java.util.stream.Collectors.toMap(
                                phase -> phase.phaseId(),
                                phase -> phase.phaseName(),
                                (left, right) -> left,
                                LinkedHashMap::new))));
        try {
            String json = mapper.writeValueAsString(definition);
            return new CompiledWorkflow(json, Sha256.digest(json));
        } catch (Exception exception) {
            throw new IllegalStateException("履约流程编译失败", exception);
        }
    }

    private static Map<String, Object> runtimeNode(
            ProjectFulfillmentNodeDraft node,
            RuntimeAssetBindings bindings
    ) {
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("nodeId", node.nodeId());
        runtime.put("nodeType", runtimeNodeType(node.nodeType()));
        runtime.put("name", node.nodeName());
        runtime.put("responsibilityRole", node.responsibilityRole());
        runtime.put("taskType", taskType(node));
        runtime.put("stageCode", requiresPhase(node.nodeType()) ? node.phaseId() : null);
        runtime.put("assigneePolicyRef", bindings.assigneePolicyRef());
        runtime.put("dispatchPolicyRef", requiresHumanTask(node.nodeType())
                ? bindings.dispatchPolicyRef() : null);
        runtime.put("formRef", node.form().isEmpty() ? null : bindings.formRef(node.nodeId()));
        runtime.put("evidenceRef", node.evidence().isEmpty()
                ? null : bindings.evidenceRef(node.nodeId()));
        runtime.put("slaRef", node.sla().isEmpty() ? null : bindings.slaRef(node.nodeId()));
        runtime.put("integrationRef", "SYSTEM_ACTION".equals(node.nodeType())
                ? bindings.integrationRef() : null);
        Map<String, Object> eventWait = node.eventWait();
        runtime.put("waitEventType", "EVENT_WAIT".equals(node.nodeType())
                ? text(eventWait.get("eventType")) : null);
        runtime.put("correlationKeyTemplate", "EVENT_WAIT".equals(node.nodeType())
                ? text(eventWait.get("correlationKeyTemplate")) : null);
        runtime.put("durationSeconds", "EVENT_WAIT".equals(node.nodeType())
                ? number(eventWait.get("maxWaitSeconds"), 0)
                : null);
        runtime.put("subProcessRef", null);
        runtime.put("multiInstance", null);
        runtime.put("compensation", null);
        runtime.put("entryActions", List.of());
        runtime.put("exitActions", List.of());
        runtime.put("retryPolicy", "SYSTEM_ACTION".equals(node.nodeType())
                ? retryPolicy(node.systemAction()) : null);
        runtime.put("failurePolicy", "SYSTEM_ACTION".equals(node.nodeType())
                ? failurePolicy(node.systemAction()) : null);
        return runtime;
    }

    private static CompiledGraph compileTransitions(ProjectFulfillmentDocument document) {
        Map<String, List<ProjectFulfillmentTransitionDraft>> outgoing = new LinkedHashMap<>();
        for (ProjectFulfillmentTransitionDraft transition : document.transitions()) {
            outgoing.computeIfAbsent(transition.fromNodeId(), ignored -> new ArrayList<>())
                    .add(transition);
        }
        Map<String, ProjectFulfillmentNodeDraft> nodeById = document.nodes().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ProjectFulfillmentNodeDraft::nodeId,
                        node -> node,
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<Map<String, Object>> runtimeNodes = new ArrayList<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<ProjectFulfillmentTransitionDraft>> entry : outgoing.entrySet()) {
            ProjectFulfillmentNodeDraft source = nodeById.get(entry.getKey());
            List<ProjectFulfillmentTransitionDraft> branches = entry.getValue().stream()
                    .sorted(Comparator.comparing(ProjectFulfillmentTransitionDraft::transitionId))
                    .toList();
            // 单一后继永远按无条件串行边编译；结果码只有在互斥分支间选择时才有意义。
            // 否则会生成只有一条条件出边的网关，让合法流程在结果值缺失时无路可走。
            boolean needsGateway = branches.size() > 1;
            if (!needsGateway) {
                for (ProjectFulfillmentTransitionDraft branch : branches) {
                    result.add(runtimeTransition(branch, branch.fromNodeId(), null));
                }
                continue;
            }
            if ("CONDITION".equals(source.nodeType())) {
                for (ProjectFulfillmentTransitionDraft branch : branches) {
                    result.add(runtimeTransition(
                            branch,
                            branch.fromNodeId(),
                            branch.defaultBranch()
                                    ? defaultCondition(source, branches)
                                    : conditionSource(source, branch)));
                }
                continue;
            }
            String gatewayId = gatewayId(source.nodeId());
            runtimeNodes.add(Map.of(
                    "nodeId", gatewayId,
                    "nodeType", "EXCLUSIVE_GATEWAY",
                    "name", source.nodeName() + "结果"));
            result.add(runtimeTransition(
                    new ProjectFulfillmentTransitionDraft(
                            "TO_" + gatewayId, source.nodeId(), gatewayId,
                            null, null, false, Map.of()),
                    source.nodeId(), null));
            for (ProjectFulfillmentTransitionDraft branch : branches) {
                result.add(runtimeTransition(
                        branch,
                        gatewayId,
                        branch.defaultBranch()
                                ? defaultCondition(source, branches)
                                : conditionSource(source, branch)));
            }
        }
        return new CompiledGraph(List.copyOf(runtimeNodes), List.copyOf(result));
    }

    private static Map<String, Object> runtimeTransition(
            ProjectFulfillmentTransitionDraft transition,
            String from,
            String conditionSource
    ) {
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("transitionId", transition.transitionId());
        runtime.put("from", from);
        runtime.put("to", transition.toNodeId());
        runtime.put("condition", conditionSource == null ? null : Map.of(
                "language", "SERVICEOS_EXPR_V1",
                "source", conditionSource));
        runtime.put("priority", transition.defaultBranch() ? 1000 : 100);
        runtime.put("label", transition.branchName());
        return runtime;
    }

    private static String conditionSource(
            ProjectFulfillmentNodeDraft source,
            ProjectFulfillmentTransitionDraft transition
    ) {
        if (transition.defaultBranch()) {
            return null;
        }
        if (transition.resultCode() != null && !transition.resultCode().isBlank()) {
            return "task.resultCode == \"" + escape(transition.resultCode()) + "\"";
        }
        Object expression = transition.condition().get("expression");
        if (expression != null && !expression.toString().isBlank()) {
            return expression.toString().trim();
        }
        if ("CONDITION".equals(source.nodeType())) {
            Object branchExpression = transition.condition().get("source");
            return branchExpression == null ? null : branchExpression.toString();
        }
        return null;
    }

    private static boolean hasBranchRule(ProjectFulfillmentTransitionDraft transition) {
        return transition.defaultBranch()
                || transition.resultCode() != null
                || !transition.condition().isEmpty();
    }

    private static String defaultCondition(
            ProjectFulfillmentNodeDraft source,
            List<ProjectFulfillmentTransitionDraft> branches
    ) {
        List<String> explicit = branches.stream()
                .filter(branch -> !branch.defaultBranch())
                .map(branch -> conditionSource(source, branch))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (explicit.isEmpty()) {
            throw new IllegalArgumentException("默认分支缺少可排除的显式条件: " + source.nodeId());
        }
        return explicit.stream()
                .map(condition -> "!(" + condition + ")")
                .collect(java.util.stream.Collectors.joining(" && "));
    }

    private static String runtimeNodeType(String nodeType) {
        return switch (nodeType) {
            case "START" -> "START";
            case "END" -> "END";
            case "HUMAN_TASK", "REVIEW" -> "USER_TASK";
            case "SYSTEM_ACTION" -> "SERVICE_TASK";
            case "EVENT_WAIT" -> "WAIT_EVENT";
            case "CONDITION" -> "EXCLUSIVE_GATEWAY";
            default -> throw new IllegalArgumentException("不支持的节点类型: " + nodeType);
        };
    }

    private static String taskType(ProjectFulfillmentNodeDraft node) {
        if (!SetLike.TASK_TYPES.contains(node.nodeType())) {
            return null;
        }
        Object configured = node.task().get("taskType");
        if (configured == null && "SYSTEM_ACTION".equals(node.nodeType())) {
            configured = node.systemAction().get("actionType");
        }
        return configured == null || configured.toString().isBlank()
                ? node.nodeId()
                : configured.toString().trim();
    }

    private static Map<String, Object> retryPolicy(Map<String, Object> systemAction) {
        Object raw = systemAction.get("retryPolicy");
        if (!(raw instanceof Map<?, ?> source)) {
            return Map.of("maxAttempts", 1, "initialDelaySeconds", 0,
                    "multiplier", 1, "maxDelaySeconds", 0);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("maxAttempts", number(source.get("maxAttempts"), 3));
        result.put("initialDelaySeconds", number(source.get("initialDelaySeconds"), 30));
        result.put("multiplier", number(source.get("multiplier"), 2));
        result.put("maxDelaySeconds", number(source.get("maxDelaySeconds"), 300));
        return result;
    }

    private static String failurePolicy(Map<String, Object> systemAction) {
        Object policy = systemAction.get("failurePolicy");
        return policy == null ? "RETRY_THEN_MANUAL" : policy.toString();
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static String gatewayId(String nodeId) {
        String value = "RESULT_" + nodeId;
        return value.length() <= 100 ? value : value.substring(0, 100);
    }

    private static String normalizeKey(String profileCode) {
        String normalized = profileCode.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "-")
                .replaceAll("-+", "-");
        if (normalized.isBlank() || !Character.isLetterOrDigit(normalized.charAt(0))) {
            return "fulfillment-" + Sha256.digest(profileCode).substring(0, 12);
        }
        return normalized;
    }

    private static boolean requiresPhase(String nodeType) {
        return !"START".equals(nodeType) && !"END".equals(nodeType);
    }

    private static boolean requiresHumanTask(String nodeType) {
        return "HUMAN_TASK".equals(nodeType) || "REVIEW".equals(nodeType);
    }

    private static String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class SetLike {
        private static final java.util.Set<String> TASK_TYPES = java.util.Set.of(
                "HUMAN_TASK", "REVIEW", "SYSTEM_ACTION");

        private SetLike() {
        }
    }

    record CompiledWorkflow(String definitionJson, String contentDigest) {
    }

    record RuntimeAssetBindings(
            String assigneePolicyRef,
            String dispatchPolicyRef,
            Map<String, String> formRefs,
            Map<String, String> evidenceRefs,
            Map<String, String> slaRefs,
            String integrationRef
    ) {
        RuntimeAssetBindings {
            formRefs = Map.copyOf(formRefs);
            evidenceRefs = Map.copyOf(evidenceRefs);
            slaRefs = Map.copyOf(slaRefs);
        }

        String formRef(String nodeId) {
            return formRefs.get(nodeId);
        }

        String evidenceRef(String nodeId) {
            return evidenceRefs.get(nodeId);
        }

        String slaRef(String nodeId) {
            return slaRefs.get(nodeId);
        }

        static RuntimeAssetBindings empty() {
            return new RuntimeAssetBindings(
                    null, null, Map.of(), Map.of(), Map.of(), null);
        }
    }

    private record CompiledGraph(
            List<Map<String, Object>> hiddenGateways,
            List<Map<String, Object>> transitions
    ) {
    }
}
