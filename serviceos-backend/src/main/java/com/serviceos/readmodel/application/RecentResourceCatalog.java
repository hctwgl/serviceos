package com.serviceos.readmodel.application;

import com.serviceos.readmodel.api.RecentResourceType;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Admin 最近访问类型/标签校验。白名单外类型与疑似敏感 displayRef 失败关闭。
 */
final class RecentResourceCatalog {
    static final String PORTAL_ADMIN = "ADMIN";
    static final int DEFAULT_LIST_LIMIT = 20;
    static final int MAX_LIST_LIMIT = 20;
    static final int FETCH_OVERSCAN = 40;
    static final int MAX_DISPLAY_REF = 120;
    static final int MAX_RESOURCE_ID = 128;
    static final int MAX_PAGE_ID = 64;

    private static final Pattern PHONE_LIKE = Pattern.compile(".*1\\d{10}.*");
    private static final Pattern PRICE_LIKE = Pattern.compile(".*[¥￥]\\s*\\d+.*|.*\\d+(\\.\\d+)?\\s*元.*");

    private RecentResourceCatalog() {
    }

    static void requireAdminPortal(String portal) {
        if (portal == null || portal.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "portal 不能为空");
        }
        if (!PORTAL_ADMIN.equals(portal.trim())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "本切片仅接受 portal=ADMIN");
        }
    }

    static RecentResourceType requireType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "resourceType 不能为空");
        }
        try {
            return RecentResourceType.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "不支持的 resourceType: " + raw.trim());
        }
    }

    static String requireResourceId(String resourceId) {
        if (resourceId == null || resourceId.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "resourceId 不能为空");
        }
        String trimmed = resourceId.trim();
        if (trimmed.length() > MAX_RESOURCE_ID) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "resourceId 过长");
        }
        return trimmed;
    }

    static String normalizePageId(String pageId) {
        if (pageId == null || pageId.isBlank()) {
            return null;
        }
        String trimmed = pageId.trim();
        if (trimmed.length() > MAX_PAGE_ID) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "pageId 过长");
        }
        return trimmed;
    }

    /**
     * 规范化 displayRef：拒绝疑似完整电话/价格；空白则回退为类型+短 id。
     */
    static String sanitizeDisplayRef(RecentResourceType type, String resourceId, String displayRef) {
        String candidate = displayRef == null ? "" : displayRef.trim();
        if (candidate.isEmpty()) {
            return fallbackLabel(type, resourceId);
        }
        if (candidate.length() > MAX_DISPLAY_REF) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "displayRef 过长");
        }
        String compact = candidate.replaceAll("\\s+", "");
        if (PHONE_LIKE.matcher(compact).matches() || PRICE_LIKE.matcher(candidate).matches()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "displayRef 不得包含完整电话或价格");
        }
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (lower.contains("address") || candidate.contains("地址") || candidate.contains("门牌")) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "displayRef 不得包含地址");
        }
        return candidate;
    }

    static String fallbackLabel(RecentResourceType type, String resourceId) {
        String shortId = resourceId.length() <= 8 ? resourceId : resourceId.substring(0, 8);
        return type.name() + " " + shortId;
    }

    static String deepLink(RecentResourceType type, String resourceId) {
        return switch (type) {
            case WORK_ORDER -> "/work-orders/" + resourceId;
            case TASK -> "/tasks/" + resourceId;
            case PROJECT -> "/projects/" + resourceId;
            case NETWORK -> "/networks/" + resourceId;
            case TECHNICIAN -> "/technicians/" + resourceId;
        };
    }

    static int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIST_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIST_LIMIT) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "limit 必须在 1～" + MAX_LIST_LIMIT + " 之间");
        }
        return limit;
    }
}
