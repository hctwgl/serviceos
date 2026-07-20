package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ProjectFulfillmentCompareChange;
import com.serviceos.configuration.api.ProjectFulfillmentCompareImpact;
import com.serviceos.configuration.api.ProjectFulfillmentImpactSummary;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 比较草稿文档与已发布文档，产出中文差异与影响说明。
 *
 * <p>不做前端猜测：无基线时诚实返回 NONE；差异来自真实字段比对。</p>
 */
@Component
final class ProjectFulfillmentCompareAnalyzer {
    private final JsonMapper mapper = JsonMapper.builder().build();

    ProjectFulfillmentCompareImpact analyze(
            UUID profileId,
            UUID draftRevisionId,
            String draftDocumentJson,
            UUID baselineRevisionId,
            String baselineVersionLabel,
            String baselineDocumentJson,
            Instant asOf
    ) {
        Map<String, Object> draft = parse(draftDocumentJson);
        List<ProjectFulfillmentCompareChange> changes = new ArrayList<>();
        List<String> risks = new ArrayList<>();
        String baselineKind;
        if (baselineRevisionId == null || baselineDocumentJson == null || baselineDocumentJson.isBlank()) {
            baselineKind = "NONE";
            changes.add(new ProjectFulfillmentCompareChange(
                    "OTHER",
                    "ADDED",
                    "当前没有已发布基线，本次发布将成为该工单类型的首个生效版本",
                    null));
        } else {
            baselineKind = "PUBLISHED";
            Map<String, Object> baseline = parse(baselineDocumentJson);
            changes.addAll(diffStages(stages(baseline), stages(draft)));
            if (!Objects.equals(baseline.get("orderTypeName"), draft.get("orderTypeName"))) {
                changes.add(new ProjectFulfillmentCompareChange(
                        "OTHER",
                        "MODIFIED",
                        "工单类型显示名称已变更",
                        String.valueOf(baseline.get("orderTypeName")) + " → "
                                + String.valueOf(draft.get("orderTypeName"))));
            }
            if (!Objects.equals(
                    String.valueOf(baseline.get("supportedClientKinds")),
                    String.valueOf(draft.get("supportedClientKinds")))) {
                changes.add(new ProjectFulfillmentCompareChange(
                        "OTHER",
                        "MODIFIED",
                        "客户端支持范围已变更",
                        null));
            }
        }
        if (stages(draft).isEmpty()) {
            risks.add("草稿尚未配置任何阶段，发布后新工单将无法进入履约路径");
        }
        if (changes.stream().anyMatch(change -> "REMOVED".equals(change.changeType())
                && "STAGE".equals(change.category()))) {
            risks.add("删除阶段只影响新工单；存量工单仍按冻结版本继续执行");
        }
        ProjectFulfillmentImpactSummary impact = new ProjectFulfillmentImpactSummary(
                "生效时间之后创建的新工单将使用本次发布版本",
                "已创建工单继续使用各自冻结的履约配置版本，不会自动迁移",
                "请在发布确认页设置生效时间");
        return new ProjectFulfillmentCompareImpact(
                profileId,
                draftRevisionId,
                baselineKind,
                baselineRevisionId,
                baselineVersionLabel,
                changes.size(),
                changes,
                impact,
                risks,
                asOf);
    }

    private List<ProjectFulfillmentCompareChange> diffStages(
            List<Map<String, Object>> baseline,
            List<Map<String, Object>> draft
    ) {
        Map<String, Map<String, Object>> baselineByCode = indexByStageCode(baseline);
        Map<String, Map<String, Object>> draftByCode = indexByStageCode(draft);
        Set<String> codes = new LinkedHashSet<>();
        codes.addAll(baselineByCode.keySet());
        codes.addAll(draftByCode.keySet());
        List<ProjectFulfillmentCompareChange> changes = new ArrayList<>();
        for (String code : codes) {
            Map<String, Object> before = baselineByCode.get(code);
            Map<String, Object> after = draftByCode.get(code);
            if (before == null) {
                changes.add(new ProjectFulfillmentCompareChange(
                        "STAGE",
                        "ADDED",
                        "新增阶段「" + stageName(after) + "」",
                        null));
                continue;
            }
            if (after == null) {
                changes.add(new ProjectFulfillmentCompareChange(
                        "STAGE",
                        "REMOVED",
                        "移除阶段「" + stageName(before) + "」",
                        null));
                continue;
            }
            if (!Objects.equals(stageName(before), stageName(after))
                    || !Objects.equals(before.get("ownerType"), after.get("ownerType"))
                    || !Objects.equals(before.get("taskType"), after.get("taskType"))
                    || !Objects.equals(before.get("sequence"), after.get("sequence"))) {
                changes.add(new ProjectFulfillmentCompareChange(
                        "STAGE",
                        "MODIFIED",
                        "阶段「" + stageName(after) + "」责任或顺序已调整",
                        null));
            }
            int beforeForms = sizeOf(before.get("formRefs"));
            int afterForms = sizeOf(after.get("formRefs"));
            if (beforeForms != afterForms) {
                changes.add(new ProjectFulfillmentCompareChange(
                        "FORM",
                        beforeForms < afterForms ? "ADDED" : "REMOVED",
                        "阶段「" + stageName(after) + "」表单配置从 " + beforeForms + " 项变为 " + afterForms + " 项",
                        null));
            }
            int beforeEvidence = sizeOf(before.get("evidenceRefs"));
            int afterEvidence = sizeOf(after.get("evidenceRefs"));
            if (beforeEvidence != afterEvidence) {
                changes.add(new ProjectFulfillmentCompareChange(
                        "EVIDENCE",
                        beforeEvidence < afterEvidence ? "ADDED" : "REMOVED",
                        "阶段「" + stageName(after) + "」资料要求从 " + beforeEvidence + " 项变为 " + afterEvidence + " 项",
                        null));
            }
            int beforeActions = sizeOf(before.get("actions"));
            int afterActions = sizeOf(after.get("actions"));
            if (beforeActions != afterActions) {
                changes.add(new ProjectFulfillmentCompareChange(
                        "ACTION",
                        beforeActions < afterActions ? "ADDED" : "REMOVED",
                        "阶段「" + stageName(after) + "」允许动作从 " + beforeActions + " 项变为 " + afterActions + " 项",
                        null));
            }
            if (!Objects.equals(String.valueOf(before.get("slaRef")), String.valueOf(after.get("slaRef")))) {
                changes.add(new ProjectFulfillmentCompareChange(
                        "SLA",
                        "MODIFIED",
                        "阶段「" + stageName(after) + "」SLA 绑定已变更",
                        null));
            }
        }
        return changes;
    }

    private Map<String, Map<String, Object>> indexByStageCode(List<Map<String, Object>> stages) {
        Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
        int i = 0;
        for (Map<String, Object> stage : stages) {
            String code = String.valueOf(stage.getOrDefault("stageCode", "stage-" + i));
            indexed.put(code, stage);
            i++;
        }
        return indexed;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> stages(Map<String, Object> document) {
        Object raw = document.get("stages");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> stages = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                stages.add((Map<String, Object>) map);
            }
        }
        return stages;
    }

    private Map<String, Object> parse(String json) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(json, Map.class);
            return map == null ? Map.of() : map;
        } catch (Exception ex) {
            throw new IllegalStateException("无法解析履约配置文档用于差异比较", ex);
        }
    }

    private static String stageName(Map<String, Object> stage) {
        Object name = stage.get("stageName");
        return name == null || name.toString().isBlank() ? "未命名阶段" : name.toString();
    }

    private static int sizeOf(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
    }
}
