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
