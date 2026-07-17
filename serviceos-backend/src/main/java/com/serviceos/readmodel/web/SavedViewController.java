package com.serviceos.readmodel.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.SavedView;
import com.serviceos.readmodel.api.SavedViewCommandService;
import com.serviceos.readmodel.api.SavedViewFilterAst;
import com.serviceos.readmodel.api.SavedViewPage;
import com.serviceos.readmodel.api.SavedViewQueryService;
import com.serviceos.readmodel.api.SavedViewSortSpec;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CorrelationIds;
import com.serviceos.shared.ProblemCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin 个人 SavedView HTTP 适配器。tenant/principal 只来自受信 CurrentPrincipal；
 * 不提供 share 端点；页面数据访问仍由各业务查询重新鉴权。
 */
@RestController
@RequestMapping("/api/v1")
final class SavedViewController {
    private final SavedViewQueryService queries;
    private final SavedViewCommandService commands;
    private final CurrentPrincipalProvider principals;

    SavedViewController(
            SavedViewQueryService queries,
            SavedViewCommandService commands,
            CurrentPrincipalProvider principals
    ) {
        this.queries = queries;
        this.commands = commands;
        this.principals = principals;
    }

    @GetMapping("/me/saved-views")
    ResponseEntity<SavedViewPage> list(
            @RequestParam("pageId") String pageId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        SavedViewPage page = queries.list(principals.current(), correlationId, pageId);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(page);
    }

    @PostMapping("/me/saved-views")
    ResponseEntity<SavedView> create(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody CreateSavedViewRequest request
    ) {
        SavedView view = commands.create(
                principals.current(),
                correlationId,
                request.pageId(),
                request.name(),
                request.schemaVersion(),
                request.filter(),
                request.sort(),
                request.columns(),
                Boolean.TRUE.equals(request.isDefault()));
        return ResponseEntity.ok()
                .eTag(Long.toString(view.aggregateVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(view);
    }

    @PutMapping("/me/saved-views/{savedViewId}")
    ResponseEntity<SavedView> update(
            @PathVariable UUID savedViewId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody UpdateSavedViewRequest request
    ) {
        SavedView view = commands.update(
                principals.current(),
                correlationId,
                savedViewId,
                parseVersion(ifMatch),
                request.name(),
                request.schemaVersion(),
                request.filter(),
                request.sort(),
                request.columns(),
                Boolean.TRUE.equals(request.isDefault()));
        return ResponseEntity.ok()
                .eTag(Long.toString(view.aggregateVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(view);
    }

    @DeleteMapping("/me/saved-views/{savedViewId}")
    ResponseEntity<Void> delete(
            @PathVariable UUID savedViewId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        commands.delete(principals.current(), correlationId, savedViewId);
        return ResponseEntity.noContent()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .build();
    }

    private static long parseVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "If-Match 不能为空");
        }
        String raw = ifMatch.trim();
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            raw = raw.substring(1, raw.length() - 1);
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "If-Match 版本无效");
        }
    }

    record CreateSavedViewRequest(
            @NotBlank @Size(max = 120) String pageId,
            @NotBlank @Size(max = 120) String name,
            @NotNull Integer schemaVersion,
            @NotNull SavedViewFilterAst filter,
            SavedViewSortSpec sort,
            List<String> columns,
            Boolean isDefault
    ) {
    }

    record UpdateSavedViewRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull Integer schemaVersion,
            @NotNull SavedViewFilterAst filter,
            SavedViewSortSpec sort,
            List<String> columns,
            Boolean isDefault
    ) {
    }
}
