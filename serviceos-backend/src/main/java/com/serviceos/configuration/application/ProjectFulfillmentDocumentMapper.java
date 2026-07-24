package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ProjectFulfillmentDocument;
import com.serviceos.configuration.api.ProjectFulfillmentMatchRule;
import com.serviceos.configuration.api.ProjectFulfillmentStageDraft;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在结构化 Draft 文档与持久化 JSON 之间做唯一服务端映射。
 *
 * <p>禁止前端自行解析 document JSON 形成第二套业务解释。</p>
 */
@Component
final class ProjectFulfillmentDocumentMapper {
    private final ObjectMapper objectMapper;

    ProjectFulfillmentDocumentMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ProjectFulfillmentDocument fromJson(String documentJson) {
        Map<String, Object> raw = parseMap(documentJson);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stageMaps = (List<Map<String, Object>>) raw.getOrDefault(
                "stages", List.of());
        List<ProjectFulfillmentStageDraft> stages = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> stage : stageMaps) {
            index++;
            stages.add(new ProjectFulfillmentStageDraft(
                    stringVal(stage.get("stageCode"), "STAGE_" + index),
                    stringVal(stage.get("stageName"), "未命名阶段"),
                    intVal(stage.get("sequence"), index),
                    stringOrNull(stage.get("stageType")),
                    stringOrNull(stage.get("taskType")),
                    stringVal(stage.get("ownerType"), "PLATFORM"),
                    stringOrNull(stage.get("description")),
                    stringList(stage.get("formRefs")),
                    stringList(stage.get("evidenceRefs")),
                    objectMapList(stage.get("actions")),
                    objectMapList(stage.get("transitions")),
                    objectMapList(stage.get("exceptionPaths")),
                    stringOrNull(stage.get("slaRef")),
                    boolVal(stage.get("terminal"))));
        }
        @SuppressWarnings("unchecked")
        List<String> clientKinds = raw.get("supportedClientKinds") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        return new ProjectFulfillmentDocument(
                stringVal(raw.get("schemaVersion"), "1.0.0"),
                stringOrNull(raw.get("orderTypeName")),
                matchRule(raw.get("matchRule")),
                clientKinds,
                stages);
    }

    String toJson(ProjectFulfillmentDocument document) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("schemaVersion", document.schemaVersion());
        if (document.orderTypeName() != null) {
            raw.put("orderTypeName", document.orderTypeName());
        }
        Map<String, Object> matchRule = new LinkedHashMap<>();
        matchRule.put("brandCodes", document.matchRule().brandCodes());
        matchRule.put("provinceCodes", document.matchRule().provinceCodes());
        raw.put("matchRule", matchRule);
        if (!document.supportedClientKinds().isEmpty()) {
            raw.put("supportedClientKinds", document.supportedClientKinds());
        }
        List<Map<String, Object>> stages = new ArrayList<>();
        int sequence = 1;
        for (ProjectFulfillmentStageDraft stage : document.stages()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stageCode", stage.stageCode());
            row.put("stageName", stage.stageName());
            row.put("sequence", stage.sequence() > 0 ? stage.sequence() : sequence);
            if (stage.stageType() != null) {
                row.put("stageType", stage.stageType());
            }
            if (stage.taskType() != null) {
                row.put("taskType", stage.taskType());
            }
            row.put("ownerType", stage.ownerType());
            if (stage.description() != null) {
                row.put("description", stage.description());
            }
            row.put("formRefs", stage.formRefs());
            row.put("evidenceRefs", stage.evidenceRefs());
            row.put("actions", stage.actions());
            row.put("transitions", stage.transitions());
            row.put("exceptionPaths", stage.exceptionPaths());
            row.put("slaRef", stage.slaRef());
            row.put("terminal", stage.terminal());
            stages.add(row);
            sequence++;
        }
        raw.put("stages", stages);
        try {
            return objectMapper.writeValueAsString(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("履约草稿文档序列化失败", ex);
        }
    }

    Map<String, Object> parseMap(String documentJson) {
        try {
            return objectMapper.readValue(documentJson, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "履约配置文档不是合法结构");
        }
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private static ProjectFulfillmentMatchRule matchRule(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return ProjectFulfillmentMatchRule.unrestricted();
        }
        return new ProjectFulfillmentMatchRule(
                stringList(map.get("brandCodes")),
                stringList(map.get("provinceCodes")));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> objectMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    private static String stringVal(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    private static String stringOrNull(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private static int intVal(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static boolean boolVal(Object value) {
        return value instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(value));
    }
}
