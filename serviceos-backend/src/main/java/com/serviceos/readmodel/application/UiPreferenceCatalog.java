package com.serviceos.readmodel.application;

import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import tools.jackson.databind.JsonNode;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Admin UI Preference 键白名单与值校验。白名单外与禁止键一律失败关闭，禁止静默忽略。
 */
final class UiPreferenceCatalog {
    static final String PORTAL_ADMIN = "ADMIN";
    static final int CURRENT_SCHEMA_VERSION = 1;

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "theme",
            "density",
            "locale",
            "reduceMotion",
            "defaultSavedViews",
            "columnWidths"
    );

    /** 显式禁止键：即使未来误入白名单也要拒绝（防御性）。 */
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "disableSecurityConfirmations",
            "hideRequiredFields",
            "bypassRedaction",
            "disableTransactionalNotifications",
            "disableSecurityAlerts",
            "skipHighRiskConfirmation"
    );

    private static final Set<String> SAVED_VIEW_PAGE_IDS = Set.of(
            "ADMIN.TASK.QUEUE",
            "ADMIN.WORKORDER.LIST",
            "ADMIN.CORRECTION.QUEUE"
    );

    private static final Set<String> THEMES = Set.of("LIGHT", "DARK", "SYSTEM");
    private static final Set<String> DENSITIES = Set.of("COMFORTABLE", "COMPACT");
    private static final Pattern LOCALE = Pattern.compile("^[a-z]{2}(-[A-Z]{2})?$");

    private UiPreferenceCatalog() {
    }

    static void requireAdminPortal(String portal) {
        if (portal == null || portal.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "portal 不能为空");
        }
        if (!PORTAL_ADMIN.equals(portal.trim())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "本切片仅接受 portal=ADMIN");
        }
    }

    static void validateWrite(String key, int schemaVersion, JsonNode value) {
        String normalizedKey = requireKey(key);
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "schemaVersion 不受支持，当前仅接受 " + CURRENT_SCHEMA_VERSION);
        }
        if (value == null || value.isNull() || value.isMissingNode()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "value 不能为空");
        }
        switch (normalizedKey) {
            case "theme" -> requireEnumString(value, THEMES, "theme");
            case "density" -> requireEnumString(value, DENSITIES, "density");
            case "locale" -> requireLocale(value);
            case "reduceMotion" -> {
                if (!value.isBoolean()) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "reduceMotion 必须为 boolean");
                }
            }
            case "defaultSavedViews" -> requireDefaultSavedViews(value);
            case "columnWidths" -> requireColumnWidths(value);
            default -> throw new BusinessProblem(ProblemCode.UI_PREFERENCE_KEY_NOT_ALLOWED,
                    "偏好键不在白名单：" + normalizedKey);
        }
    }

    static String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "preference key 不能为空");
        }
        String normalized = key.trim();
        if (FORBIDDEN_KEYS.contains(normalized)
                || normalized.toLowerCase(Locale.ROOT).contains("bypass")
                || normalized.toLowerCase(Locale.ROOT).contains("disableSecurity")
                || normalized.toLowerCase(Locale.ROOT).contains("hideRequired")) {
            throw new BusinessProblem(ProblemCode.UI_PREFERENCE_KEY_NOT_ALLOWED,
                    "禁止的偏好键：" + normalized);
        }
        if (!ALLOWED_KEYS.contains(normalized)) {
            throw new BusinessProblem(ProblemCode.UI_PREFERENCE_KEY_NOT_ALLOWED,
                    "偏好键不在白名单：" + normalized);
        }
        return normalized;
    }

    private static void requireEnumString(JsonNode value, Set<String> allowed, String field) {
        if (!value.isString() || !allowed.contains(value.asString())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    field + " 必须为 " + allowed);
        }
    }

    private static void requireLocale(JsonNode value) {
        if (!value.isString() || !LOCALE.matcher(value.asString()).matches()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "locale 必须为 BCP-47 简写（如 zh-CN、en）");
        }
    }

    private static void requireDefaultSavedViews(JsonNode value) {
        if (!value.isObject()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "defaultSavedViews 必须为对象");
        }
        var fields = value.properties();
        if (fields.isEmpty()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "defaultSavedViews 不能为空对象");
        }
        for (Map.Entry<String, JsonNode> entry : fields) {
            if (!SAVED_VIEW_PAGE_IDS.contains(entry.getKey())) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "defaultSavedViews 含未接受 pageId：" + entry.getKey());
            }
            JsonNode idNode = entry.getValue();
            if (idNode == null || idNode.isNull()) {
                continue;
            }
            if (!idNode.isString()) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "defaultSavedViews 值必须为 uuid 或 null");
            }
            try {
                UUID.fromString(idNode.asString());
            } catch (IllegalArgumentException ex) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "defaultSavedViews 值必须为合法 uuid");
            }
        }
    }

    private static void requireColumnWidths(JsonNode value) {
        if (!value.isObject()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "columnWidths 必须为对象");
        }
        JsonNode schemaNode = value.get("schemaVersion");
        if (schemaNode == null || !schemaNode.isIntegralNumber() || schemaNode.asInt() < 1) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "columnWidths.schemaVersion 必须为正整数");
        }
        JsonNode pages = value.get("pages");
        if (pages == null || !pages.isObject()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "columnWidths.pages 必须为对象");
        }
        for (Map.Entry<String, JsonNode> page : pages.properties()) {
            if (!SAVED_VIEW_PAGE_IDS.contains(page.getKey())) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "columnWidths 含未接受 pageId：" + page.getKey());
            }
            if (!page.getValue().isObject()) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "columnWidths.pages 值必须为列宽对象");
            }
            for (Map.Entry<String, JsonNode> col : page.getValue().properties()) {
                if (col.getKey() == null || col.getKey().isBlank() || col.getKey().length() > 64) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "columnId 无效");
                }
                JsonNode width = col.getValue();
                if (width == null || !width.isIntegralNumber() || width.asInt() < 40 || width.asInt() > 2000) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "列宽必须为 40～2000 的整数像素");
                }
            }
        }
    }
}
