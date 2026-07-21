package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ProjectFulfillmentRunbook;
import com.serviceos.configuration.api.ProjectFulfillmentRunbookStage;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将编译后的 Manifest JSON 转为产品化 Runbook。
 *
 * <p>保证前端无需解析 Manifest 即可展示阶段、责任、表单、资料、动作与 SLA 摘要。</p>
 */
@Component
final class ProjectFulfillmentRunbookAssembler {
    private final JsonMapper mapper = JsonMapper.builder().build();

    ProjectFulfillmentRunbook fromManifestJson(String manifestJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> manifest = mapper.readValue(manifestJson, Map.class);
            return fromManifest(manifest);
        } catch (Exception ex) {
            throw new IllegalStateException("无法从 Manifest 生成运行说明书", ex);
        }
    }

    ProjectFulfillmentRunbook fromManifest(Map<String, Object> manifest) {
        String serviceProductCode = stringVal(manifest.get("serviceProductCode"));
        String profileName = stringVal(manifest.get("profileName"));
        String orderTypeName = stringVal(manifest.get("orderTypeName"));
        String versionLabel = stringVal(manifest.get("version"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) manifest.getOrDefault(
                "stages", List.of());
        List<ProjectFulfillmentRunbookStage> rows = new ArrayList<>();
        List<Map<String, Object>> ordered = stages.stream()
                .sorted(Comparator.comparingInt(stage -> intVal(stage.get("sequence"), 0)))
                .toList();
        Map<Integer, String> nextBySequence = new LinkedHashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            Map<String, Object> stage = ordered.get(i);
            String next = i + 1 < ordered.size()
                    ? stringVal(ordered.get(i + 1).get("stageName"))
                    : (boolVal(stage.get("terminal")) ? "结束" : "未配置");
            nextBySequence.put(intVal(stage.get("sequence"), i + 1), next);
        }
        for (Map<String, Object> stage : ordered) {
            int sequence = intVal(stage.get("sequence"), rows.size() + 1);
            List<?> formRefs = listVal(stage.get("formRefs"));
            List<?> evidenceRefs = listVal(stage.get("evidenceRefs"));
            List<?> actions = listVal(stage.get("actions"));
            List<?> exceptions = listVal(stage.get("exceptionPaths"));
            rows.add(new ProjectFulfillmentRunbookStage(
                    firstNonBlank(stringVal(stage.get("stageName")), "未命名阶段"),
                    sequence,
                    ownerLabel(stringVal(stage.get("ownerType"))),
                    taskLabel(stringVal(stage.get("taskType"))),
                    formRefs.size(),
                    countSummary(formRefs.size(), "表单"),
                    evidenceRefs.size(),
                    countSummary(evidenceRefs.size(), "必传资料槽位"),
                    actions.size(),
                    countSummary(actions.size(), "允许动作"),
                    nextBySequence.getOrDefault(sequence, "未配置"),
                    exceptions.isEmpty() ? "无异常出口" : "异常出口 " + exceptions.size() + " 条",
                    firstNonBlank(stringVal(stage.get("slaRef")), "未绑定 SLA"),
                    boolVal(stage.get("terminal"))));
        }
        String clientSupport = clientSupportSummary(manifest);
        return new ProjectFulfillmentRunbook(
                firstNonBlank(profileName, "未命名配置"),
                serviceProductCode,
                serviceProductLabel(serviceProductCode),
                orderTypeName,
                versionLabel,
                rows.size(),
                rows,
                clientSupport,
                "发布后仅影响生效时间之后的新工单；存量工单继续使用创建时冻结的配置版本。");
    }

    private static String clientSupportSummary(Map<String, Object> manifest) {
        Object raw = manifest.get("supportedClientKinds");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return "未声明客户端支持范围";
        }
        List<String> labels = new ArrayList<>();
        for (Object item : list) {
            labels.add(clientKindLabel(String.valueOf(item)));
        }
        return "支持客户端：" + String.join("、", labels);
    }

    private static String serviceProductLabel(String code) {
        return switch (code == null ? "" : code) {
            case "HOME_CHARGING_SURVEY_INSTALL" -> "家充勘测安装";
            case "SURVEY" -> "勘测服务";
            case "INSTALLATION" -> "安装服务";
            case "REPAIR" -> "维修服务";
            case "CORRECTION" -> "整改服务";
            case "SECOND_VISIT" -> "二次上门";
            default -> firstNonBlank(code, "未指定工单类型");
        };
    }

    private static String ownerLabel(String ownerType) {
        return switch (ownerType == null ? "" : ownerType) {
            case "PLATFORM" -> "平台";
            case "NETWORK" -> "网点";
            case "TECHNICIAN" -> "师傅";
            case "SYSTEM" -> "系统";
            default -> firstNonBlank(ownerType, "未指定责任");
        };
    }

    private static String taskLabel(String taskType) {
        return switch (taskType == null ? "" : taskType) {
            case "DISPATCH" -> "派单任务";
            case "SURVEY" -> "勘测任务";
            case "INSTALL" -> "安装任务";
            case "REVIEW" -> "审核任务";
            case "CORRECTION" -> "整改任务";
            default -> firstNonBlank(taskType, "未绑定任务类型");
        };
    }

    private static String clientKindLabel(String kind) {
        return switch (kind) {
            case "ADMIN_WEB" -> "Admin Web";
            case "NETWORK_WEB" -> "网点 Web";
            case "TECHNICIAN_WEB" -> "师傅 H5";
            case "TECHNICIAN_IOS" -> "师傅 iOS";
            default -> kind;
        };
    }

    private static String countSummary(int count, String noun) {
        return count == 0 ? "未配置" + noun : noun + " " + count + " 项";
    }

    private static List<?> listVal(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
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

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
