package com.serviceos.readmodel.application;

import com.serviceos.readmodel.api.SavedViewFilterAst;
import com.serviceos.readmodel.api.SavedViewFilterClause;
import com.serviceos.readmodel.api.SavedViewSortSpec;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Admin 页面筛选目录：仅允许对应 OpenAPI query 已 Accepted 的字段/操作符。
 * schemaVersion 不兼容时由服务层抛出 SAVED_VIEW_SCHEMA_OUTDATED。
 */
final class SavedViewFilterCatalog {
    private static final Pattern CLIENT_CODE = Pattern.compile("^[\\p{Alnum}._-]{1,128}$");

    private static final Map<String, PageCatalog> PAGES = Map.of(
            "ADMIN.TASK.QUEUE", new PageCatalog(
                    1,
                    Map.of(
                            "status", Set.of(
                                    "READY", "PENDING", "CLAIMED", "RUNNING", "RETRY_WAIT",
                                    "SUCCEEDED", "COMPLETED", "MANUAL_INTERVENTION", "CANCELLED"),
                            "taskKind", Set.of("HUMAN", "AUTOMATED"),
                            "assignee", Set.of("me"),
                            "projectId", Set.of()),
                    Set.of()),
            "ADMIN.WORKORDER.LIST", new PageCatalog(
                    1,
                    Map.of(
                            "status", Set.of("RECEIVED", "ACTIVE", "FULFILLED"),
                            "clientCode", Set.of(),
                            "projectId", Set.of()),
                    Set.of()),
            "ADMIN.CORRECTION.QUEUE", new PageCatalog(
                    1,
                    Map.of(
                            "status", Set.of("OPEN", "IN_PROGRESS", "RESUBMITTED", "CLOSED", "WAIVED"),
                            "projectId", Set.of(),
                            "taskId", Set.of(),
                            "sourceReviewCaseId", Set.of()),
                    Set.of())
    );

    private SavedViewFilterCatalog() {
    }

    static boolean supports(String pageId) {
        return PAGES.containsKey(pageId);
    }

    static int currentSchemaVersion(String pageId) {
        return require(pageId).schemaVersion();
    }

    static void validateCreate(String pageId, int schemaVersion, SavedViewFilterAst filter, SavedViewSortSpec sort) {
        PageCatalog catalog = require(pageId);
        if (schemaVersion != catalog.schemaVersion()) {
            throw new BusinessProblem(ProblemCode.SAVED_VIEW_SCHEMA_OUTDATED,
                    "保存视图 schemaVersion 与当前页面筛选目录不兼容，请按最新目录重建");
        }
        validateFilter(catalog, filter);
        validateSort(catalog, sort);
    }

    static void validateUpdate(
            String pageId,
            int storedSchemaVersion,
            int requestSchemaVersion,
            SavedViewFilterAst filter,
            SavedViewSortSpec sort
    ) {
        PageCatalog catalog = require(pageId);
        if (storedSchemaVersion != catalog.schemaVersion() || requestSchemaVersion != catalog.schemaVersion()) {
            throw new BusinessProblem(ProblemCode.SAVED_VIEW_SCHEMA_OUTDATED,
                    "保存视图字段目录已变化，需重置或按最新 schema 重建");
        }
        validateFilter(catalog, filter);
        validateSort(catalog, sort);
    }

    private static PageCatalog require(String pageId) {
        PageCatalog catalog = PAGES.get(pageId);
        if (catalog == null) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "pageId 不受支持或未接受个人 SavedView");
        }
        return catalog;
    }

    private static void validateFilter(PageCatalog catalog, SavedViewFilterAst filter) {
        if (filter == null) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "filter 不能为空");
        }
        for (SavedViewFilterClause clause : filter.clauses()) {
            if (clause == null || blank(clause.field()) || blank(clause.operator()) || clause.value() == null) {
                throw new BusinessProblem(ProblemCode.QUERY_FILTER_NOT_ALLOWED, "筛选子句字段/操作符/值无效");
            }
            String field = clause.field().trim();
            String operator = clause.operator().trim().toUpperCase(Locale.ROOT);
            if (!"EQ".equals(operator)) {
                throw new BusinessProblem(ProblemCode.QUERY_FILTER_NOT_ALLOWED,
                        "筛选操作符不允许: " + clause.operator());
            }
            if (!catalog.fields().containsKey(field)) {
                throw new BusinessProblem(ProblemCode.QUERY_FILTER_NOT_ALLOWED,
                        "筛选字段不允许: " + field);
            }
            validateValue(field, clause.value().trim(), catalog.fields().get(field));
        }
    }

    private static void validateSort(PageCatalog catalog, SavedViewSortSpec sort) {
        if (sort == null || sort.fields().isEmpty()) {
            return;
        }
        for (SavedViewSortSpec.SavedViewSortField field : sort.fields()) {
            if (field == null || blank(field.field()) || blank(field.direction())) {
                throw new BusinessProblem(ProblemCode.QUERY_FILTER_NOT_ALLOWED, "排序字段无效");
            }
            String name = field.field().trim();
            if (!catalog.fields().containsKey(name) && !catalog.sortOnlyFields().contains(name)) {
                throw new BusinessProblem(ProblemCode.QUERY_FILTER_NOT_ALLOWED, "排序字段不允许: " + name);
            }
            String direction = field.direction().trim().toUpperCase(Locale.ROOT);
            if (!"ASC".equals(direction) && !"DESC".equals(direction)) {
                throw new BusinessProblem(ProblemCode.QUERY_FILTER_NOT_ALLOWED, "排序方向不允许");
            }
        }
    }

    private static void validateValue(String field, String value, Set<String> enumValues) {
        if (value.isBlank()) {
            throw new BusinessProblem(ProblemCode.QUERY_FILTER_NOT_ALLOWED, "筛选值不能为空: " + field);
        }
        if (!enumValues.isEmpty()) {
            if (!enumValues.contains(value)) {
                throw new BusinessProblem(ProblemCode.QUERY_FILTER_NOT_ALLOWED,
                        "筛选值不允许: " + field);
            }
            return;
        }
        switch (field) {
            case "projectId", "taskId", "sourceReviewCaseId" -> {
                try {
                    UUID.fromString(value);
                } catch (IllegalArgumentException ex) {
                    throw new BusinessProblem(ProblemCode.QUERY_FILTER_NOT_ALLOWED,
                            "筛选值格式无效: " + field);
                }
            }
            case "clientCode" -> {
                if (!CLIENT_CODE.matcher(value).matches()) {
                    throw new BusinessProblem(ProblemCode.QUERY_FILTER_NOT_ALLOWED,
                            "筛选值格式无效: " + field);
                }
            }
            default -> throw new BusinessProblem(ProblemCode.QUERY_FILTER_NOT_ALLOWED,
                    "筛选字段不允许: " + field);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record PageCatalog(
            int schemaVersion,
            Map<String, Set<String>> fields,
            Set<String> sortOnlyFields
    ) {
    }
}
