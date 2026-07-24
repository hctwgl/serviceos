package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ProjectFulfillmentValidationIssue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 履约草稿校验。V1 覆盖可达性/责任/引用存在性等核心门禁；完整规则在后续切片补强。
 */
@Component
final class ProjectFulfillmentDraftValidator {

    List<ProjectFulfillmentValidationIssue> validate(
            UUID profileId,
            Map<String, Object> document,
            Map<String, Object> workflowDefinition,
            boolean bundlePresent
    ) {
        List<ProjectFulfillmentValidationIssue> issues = new ArrayList<>();
        String profileKey = profileId.toString();
        if (document.get("nodes") instanceof List<?> nodes && !nodes.isEmpty()) {
            validateSerialGraph(profileKey, document, issues);
            return issues;
        }
        if (workflowDefinition == null) {
            issues.add(issue("ERROR", "WORKFLOW_REF_MISSING", profileKey, null, "WORKFLOW", null,
                    "workflowAssetVersionId",
                    "尚未绑定流程版本",
                    "workflowAssetVersionId is required before publish",
                    "请选择已发布的 Workflow 版本"));
        }
        if (!bundlePresent) {
            issues.add(issue("ERROR", "BUNDLE_REF_MISSING", profileKey, null, "BUNDLE", null,
                    "sourceBundleId",
                    "尚未绑定配置 Bundle",
                    "sourceBundleId is required before publish",
                    "请绑定已发布 Bundle 或先完成资产发布"));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) document.get("stages");
        if (stages == null || stages.isEmpty()) {
            issues.add(issue("ERROR", "STAGES_EMPTY", profileKey, null, null, null, "stages",
                    "至少需要配置一个阶段",
                    "stages must not be empty",
                    "请从标准模板创建或新增阶段"));
            return issues;
        }
        Set<String> codes = new HashSet<>();
        boolean hasTerminal = false;
        for (Map<String, Object> stage : stages) {
            String stageCode = string(stage.get("stageCode"));
            if (stageCode == null) {
                issues.add(issue("ERROR", "STAGE_CODE_MISSING", profileKey, null, null, null,
                        "stages.stageCode", "阶段编码缺失", "stageCode required", "请补充阶段编码"));
                continue;
            }
            if (!codes.add(stageCode)) {
                issues.add(issue("ERROR", "STAGE_CODE_DUPLICATE", profileKey, stageCode, null, null,
                        "stages.stageCode", "阶段编码重复：" + stageCode,
                        "duplicate stageCode", "请修改为唯一编码"));
            }
            if (string(stage.get("ownerType")) == null) {
                issues.add(issue("ERROR", "STAGE_OWNER_MISSING", profileKey, stageCode, null, null,
                        "stages.ownerType", "阶段未配置责任类型",
                        "ownerType required", "请选择责任类型"));
            }
            Object terminal = stage.get("terminal");
            if (Boolean.TRUE.equals(terminal) || "END".equalsIgnoreCase(string(stage.get("stageType")))) {
                hasTerminal = true;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> actions = (List<Map<String, Object>>) stage.get("actions");
            if (actions != null) {
                for (Map<String, Object> action : actions) {
                    String target = string(action.get("targetStage"));
                    if (target != null && stages.stream().noneMatch(
                            s -> target.equals(string(s.get("stageCode"))))) {
                        issues.add(issue("ERROR", "ACTION_TARGET_STAGE_MISSING", profileKey, stageCode,
                                null, string(action.get("actionCode")), "actions.targetStage",
                                "动作目标阶段不存在：" + target,
                                "targetStage not found: " + target,
                                "请选择已有阶段或先新增目标阶段"));
                    }
                }
            }
        }
        if (!hasTerminal) {
            issues.add(issue("WARNING", "NO_TERMINAL_STAGE", profileKey, null, null, null, "stages",
                    "未标记完成阶段",
                    "no terminal stage",
                    "建议至少标记一个完成阶段"));
        }
        if (workflowDefinition != null) {
            validateWorkflowStageCoverage(profileKey, codes, workflowDefinition, issues);
        }
        return issues;
    }

    @SuppressWarnings("unchecked")
    private static void validateSerialGraph(
            String profileKey,
            Map<String, Object> document,
            List<ProjectFulfillmentValidationIssue> issues
    ) {
        List<Map<String, Object>> phases = mapList(document.get("phases"));
        List<Map<String, Object>> nodes = mapList(document.get("nodes"));
        List<Map<String, Object>> transitions = mapList(document.get("transitions"));
        Map<String, Map<String, Object>> phaseById = new java.util.LinkedHashMap<>();
        Map<String, Map<String, Object>> nodeById = new java.util.LinkedHashMap<>();

        for (Map<String, Object> phase : phases) {
            String phaseId = string(phase.get("phaseId"));
            if (phaseId == null || phaseById.putIfAbsent(phaseId, phase) != null) {
                issues.add(graphIssue("ERROR", "PHASE_ID_INVALID", profileKey, phaseId, null, null,
                        "phases.phaseId", "履约阶段编码缺失或重复", "请为每个阶段设置唯一编码"));
            }
        }

        List<String> startIds = new ArrayList<>();
        List<String> endIds = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            String nodeId = string(node.get("nodeId"));
            String nodeType = string(node.get("nodeType"));
            if (nodeId == null || nodeById.putIfAbsent(nodeId, node) != null) {
                issues.add(graphIssue("ERROR", "NODE_ID_INVALID", profileKey, null, nodeId, null,
                        "nodes.nodeId", "节点编码缺失或重复", "请为每个节点设置唯一编码"));
                continue;
            }
            if ("START".equals(nodeType)) {
                startIds.add(nodeId);
            } else if ("END".equals(nodeType)) {
                endIds.add(nodeId);
            } else {
                String phaseId = string(node.get("phaseId"));
                if (phaseId == null || !phaseById.containsKey(phaseId)) {
                    issues.add(graphIssue("ERROR", "NODE_PHASE_MISSING", profileKey, phaseId, nodeId, null,
                            "nodes.phaseId", "节点尚未归入有效履约阶段", "请选择节点所属阶段"));
                }
            }
            validateNodeConfiguration(profileKey, node, issues);
        }
        if (startIds.size() != 1) {
            issues.add(graphIssue("ERROR", "START_NODE_COUNT_INVALID", profileKey, null, null, null,
                    "nodes", "流程必须且只能有一个开始节点", "请保留一个开始节点"));
        }
        if (endIds.isEmpty()) {
            issues.add(graphIssue("ERROR", "END_NODE_MISSING", profileKey, null, null, null,
                    "nodes", "流程至少需要一个结束节点", "请添加结束节点"));
        }

        Map<String, List<String>> outgoing = new java.util.LinkedHashMap<>();
        Map<String, Integer> incoming = new java.util.LinkedHashMap<>();
        Set<String> transitionIds = new HashSet<>();
        for (Map<String, Object> transition : transitions) {
            String transitionId = string(transition.get("transitionId"));
            String from = string(transition.get("fromNodeId"));
            String to = string(transition.get("toNodeId"));
            if (transitionId == null || !transitionIds.add(transitionId)) {
                issues.add(graphIssue("ERROR", "TRANSITION_ID_INVALID", profileKey, null, from, transitionId,
                        "transitions.transitionId", "连线编码缺失或重复", "请重新创建该连线"));
            }
            if (!nodeById.containsKey(from) || !nodeById.containsKey(to) || from.equals(to)) {
                issues.add(graphIssue("ERROR", "TRANSITION_ENDPOINT_INVALID", profileKey, null, from, transitionId,
                        "transitions", "连线端点不存在或不能连接节点自身", "请重新连接有效节点"));
                continue;
            }
            if ("END".equals(string(nodeById.get(from).get("nodeType")))
                    || "START".equals(string(nodeById.get(to).get("nodeType")))) {
                issues.add(graphIssue("ERROR", "TRANSITION_DIRECTION_INVALID", profileKey, null, from, transitionId,
                        "transitions", "开始节点不能有入边，结束节点不能有出边", "请修正连线方向"));
            }
            outgoing.computeIfAbsent(from, ignored -> new ArrayList<>()).add(to);
            incoming.merge(to, 1, Integer::sum);
        }
        for (Map.Entry<String, Map<String, Object>> entry : nodeById.entrySet()) {
            String nodeId = entry.getKey();
            String nodeType = string(entry.getValue().get("nodeType"));
            if (!"START".equals(nodeType) && incoming.getOrDefault(nodeId, 0) == 0) {
                issues.add(graphIssue("ERROR", "NODE_ISOLATED", profileKey, null, nodeId, null,
                        "transitions", "节点没有入边", "请连接上一个节点"));
            }
            if (!"END".equals(nodeType) && outgoing.getOrDefault(nodeId, List.of()).isEmpty()) {
                issues.add(graphIssue("ERROR", "NODE_NO_EXIT", profileKey, null, nodeId, null,
                        "transitions", "节点没有后续路径", "请连接下一节点"));
            }
        }
        if (startIds.size() == 1) {
            Set<String> reachable = reachable(startIds.getFirst(), outgoing);
            for (String nodeId : nodeById.keySet()) {
                if (!reachable.contains(nodeId)) {
                    issues.add(graphIssue("ERROR", "NODE_UNREACHABLE", profileKey, null, nodeId, null,
                            "transitions", "节点从开始节点不可达", "请补充有效流转路径"));
                }
            }
            Set<String> canReachEnd = reverseReachable(endIds, transitions);
            for (String nodeId : reachable) {
                if (!canReachEnd.contains(nodeId)) {
                    issues.add(graphIssue("ERROR", "PATH_WITHOUT_END", profileKey, null, nodeId, null,
                            "transitions", "该节点存在无法到达结束节点的路径", "请补充终点或修正循环"));
                }
            }
        }
        validateExclusiveBranches(profileKey, outgoing, nodeById, transitions, issues);
    }

    private static void validateNodeConfiguration(
            String profileKey,
            Map<String, Object> node,
            List<ProjectFulfillmentValidationIssue> issues
    ) {
        String nodeId = string(node.get("nodeId"));
        String phaseId = string(node.get("phaseId"));
        String type = string(node.get("nodeType"));
        if ("HUMAN_TASK".equals(type) || "REVIEW".equals(type)) {
            if (map(node.get("task")).isEmpty()) {
                issues.add(graphIssue("ERROR", "TASK_MISSING", profileKey, phaseId, nodeId, null,
                        "task", "人工节点尚未创建任务", "创建任务或从公共模板复制"));
            }
            if (string(node.get("responsibilityRole")) == null) {
                issues.add(graphIssue("ERROR", "RESPONSIBILITY_MISSING", profileKey, phaseId, nodeId, null,
                        "responsibilityRole", "人工节点尚未配置责任角色", "请选择责任角色"));
            }
        }
        if ("REVIEW".equals(type)) {
            List<String> results = stringList(node.get("completionResults"));
            if (!results.contains("PASS") || !results.contains("REJECT")) {
                issues.add(graphIssue("ERROR", "REVIEW_RESULTS_MISSING", profileKey, phaseId, nodeId, null,
                        "completionResults", "审核节点必须配置 PASS 与 REJECT 结果", "补充审核结果"));
            }
        }
        if ("SYSTEM_ACTION".equals(type)) {
            Map<String, Object> config = map(node.get("systemAction"));
            if (string(config.get("actionType")) == null
                    || string(config.get("idempotencyStrategy")) == null
                    || map(config.get("retryPolicy")).isEmpty()
                    || string(config.get("failureResult")) == null) {
                issues.add(graphIssue("ERROR", "SYSTEM_ACTION_INCOMPLETE", profileKey, phaseId, nodeId, null,
                        "systemAction", "系统动作的幂等、重试或失败策略不完整", "完善系统动作配置"));
            }
        }
        if ("EVENT_WAIT".equals(type)) {
            Map<String, Object> config = map(node.get("eventWait"));
            if (string(config.get("eventType")) == null
                    || string(config.get("correlationKeyTemplate")) == null
                    || intVal(config.get("maxWaitSeconds"), 0) < 1
                    || string(config.get("timeoutStrategy")) == null) {
                issues.add(graphIssue("ERROR", "EVENT_WAIT_INCOMPLETE", profileKey, phaseId, nodeId, null,
                        "eventWait", "事件等待的事件、匹配规则或超时策略不完整", "完善事件等待配置"));
            }
        }
        if ("CONDITION".equals(type)) {
            Map<String, Object> config = map(node.get("condition"));
            if (string(config.get("dataSource")) == null || string(config.get("field")) == null) {
                issues.add(graphIssue("ERROR", "CONDITION_INCOMPLETE", profileKey, phaseId, nodeId, null,
                        "condition", "条件节点尚未配置业务数据来源和字段", "完善条件规则"));
            }
        }
    }

    private static void validateExclusiveBranches(
            String profileKey,
            Map<String, List<String>> outgoing,
            Map<String, Map<String, Object>> nodeById,
            List<Map<String, Object>> transitions,
            List<ProjectFulfillmentValidationIssue> issues
    ) {
        for (Map.Entry<String, List<String>> entry : outgoing.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            String nodeId = entry.getKey();
            String type = string(nodeById.get(nodeId).get("nodeType"));
            List<Map<String, Object>> branches = transitions.stream()
                    .filter(transition -> nodeId.equals(string(transition.get("fromNodeId"))))
                    .toList();
            long defaults = branches.stream().filter(branch -> boolVal(branch.get("defaultBranch"))).count();
            Set<String> resultCodes = new HashSet<>();
            boolean duplicate = branches.stream()
                    .map(branch -> string(branch.get("resultCode")))
                    .filter(java.util.Objects::nonNull)
                    .anyMatch(code -> !resultCodes.add(code));
            boolean branchCapable = Set.of("REVIEW", "CONDITION", "SYSTEM_ACTION", "EVENT_WAIT", "HUMAN_TASK")
                    .contains(type);
            if (!branchCapable || defaults > 1 || duplicate
                    || branches.stream().anyMatch(branch -> string(branch.get("resultCode")) == null
                    && map(branch.get("condition")).isEmpty()
                    && !boolVal(branch.get("defaultBranch")))) {
                issues.add(graphIssue("ERROR", "BRANCH_NOT_EXCLUSIVE", profileKey,
                        string(nodeById.get(nodeId).get("phaseId")), nodeId, null,
                        "transitions", "多条出边必须是可证明互斥的结果或条件分支",
                        "为每条分支配置唯一结果，并最多保留一个默认分支"));
            }
        }
    }

    private static Set<String> reachable(String start, Map<String, List<String>> outgoing) {
        Set<String> result = new HashSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!result.add(current)) {
                continue;
            }
            queue.addAll(outgoing.getOrDefault(current, List.of()));
        }
        return result;
    }

    private static Set<String> reverseReachable(
            List<String> ends,
            List<Map<String, Object>> transitions
    ) {
        Map<String, List<String>> reverse = new java.util.LinkedHashMap<>();
        for (Map<String, Object> transition : transitions) {
            String from = string(transition.get("fromNodeId"));
            String to = string(transition.get("toNodeId"));
            if (from != null && to != null) {
                reverse.computeIfAbsent(to, ignored -> new ArrayList<>()).add(from);
            }
        }
        Set<String> result = new HashSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>(ends);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!result.add(current)) {
                continue;
            }
            queue.addAll(reverse.getOrDefault(current, List.of()));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private static int intVal(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(value.toString());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static boolean boolVal(Object value) {
        return value instanceof Boolean bool
                ? bool
                : "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static ProjectFulfillmentValidationIssue graphIssue(
            String severity,
            String code,
            String profileId,
            String phaseId,
            String nodeId,
            String transitionId,
            String fieldPath,
            String message,
            String suggestion
    ) {
        return new ProjectFulfillmentValidationIssue(
                severity, code, profileId, null, null, null, fieldPath, message,
                code, suggestion, phaseId, nodeId, transitionId, fieldPath);
    }

    private static void validateWorkflowStageCoverage(
            String profileKey,
            Set<String> documentStageCodes,
            Map<String, Object> workflowDefinition,
            List<ProjectFulfillmentValidationIssue> issues
    ) {
        Set<String> workflowStageCodes = new LinkedHashSet<>();
        Object rawNodes = workflowDefinition.get("nodes");
        if (rawNodes instanceof List<?> nodes) {
            for (Object rawNode : nodes) {
                if (!(rawNode instanceof Map<?, ?> node)) {
                    continue;
                }
                String nodeType = string(node.get("nodeType"));
                if ("START".equals(nodeType) || "END".equals(nodeType)) {
                    continue;
                }
                String stageCode = string(node.get("stageCode"));
                if (stageCode != null) {
                    workflowStageCodes.add(stageCode);
                }
            }
        }
        for (String stageCode : documentStageCodes) {
            if (!workflowStageCodes.contains(stageCode)) {
                issues.add(issue(
                        "ERROR", "STAGE_NOT_IN_WORKFLOW", profileKey, stageCode,
                        "WORKFLOW", null, "stages.stageCode",
                        "阶段“" + stageCode + "”不在当前运行流程中",
                        "document stage is not present in workflow definition",
                        "请从绑定流程同步阶段，或先发布包含该阶段的新流程版本"));
            }
        }
        for (String stageCode : workflowStageCodes) {
            if (!documentStageCodes.contains(stageCode)) {
                issues.add(issue(
                        "ERROR", "WORKFLOW_STAGE_NOT_DESCRIBED", profileKey, stageCode,
                        "WORKFLOW", null, "stages",
                        "运行流程阶段“" + stageCode + "”尚未纳入履约方案",
                        "workflow stage is missing from fulfillment document",
                        "请把该运行阶段加入方案后再发布"));
            }
        }
    }

    private static ProjectFulfillmentValidationIssue issue(
            String severity, String code, String profileId, String stageCode,
            String assetType, String assetRef, String fieldPath,
            String userMessage, String technical, String suggestion
    ) {
        return new ProjectFulfillmentValidationIssue(
                severity, code, profileId, stageCode, assetType, assetRef, fieldPath,
                userMessage, technical, suggestion);
    }

    private static String string(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
