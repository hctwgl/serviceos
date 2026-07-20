package com.serviceos.readmodel.application;

import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;

import java.util.UUID;

/** Admin 关注项目白名单与展示约束。 */
final class FollowedProjectCatalog {
    static final String PORTAL_ADMIN = "ADMIN";
    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;
    static final int FETCH_OVERSCAN = 80;
    static final int MAX_DISPLAY_LEN = 120;

    private FollowedProjectCatalog() {
    }

    static void requireAdminPortal(String portal) {
        if (portal == null || !PORTAL_ADMIN.equals(portal.trim())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "portal 仅支持 ADMIN");
        }
    }

    static int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "limit 必须在 1～" + MAX_LIMIT);
        }
        return limit;
    }

    static String sanitizeDisplayRef(UUID projectId, String displayRef) {
        if (displayRef == null || displayRef.isBlank()) {
            return fallbackLabel(projectId);
        }
        String trimmed = displayRef.trim();
        if (trimmed.length() > MAX_DISPLAY_LEN) {
            trimmed = trimmed.substring(0, MAX_DISPLAY_LEN);
        }
        String lower = trimmed.toLowerCase();
        if (lower.contains("bearer ") || lower.contains("password") || lower.contains("secret")) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "displayRef 含敏感内容");
        }
        return trimmed;
    }

    static String fallbackLabel(UUID projectId) {
        String id = projectId.toString();
        return "项目 " + id.substring(0, 8);
    }

    static String deepLink(UUID projectId) {
        return "/projects/" + projectId;
    }
}
