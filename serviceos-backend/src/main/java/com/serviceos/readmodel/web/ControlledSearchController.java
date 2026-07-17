package com.serviceos.readmodel.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.ControlledSearchQueryService;
import com.serviceos.readmodel.api.ControlledSearchResult;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 受控搜索 HTTP 适配器。tenant/principal 只来自受信 CurrentPrincipal；
 * 不回显完整敏感 q。
 */
@RestController
@RequestMapping("/api/v1")
final class ControlledSearchController {
    private final ControlledSearchQueryService searches;
    private final CurrentPrincipalProvider principals;

    ControlledSearchController(
            ControlledSearchQueryService searches,
            CurrentPrincipalProvider principals
    ) {
        this.searches = searches;
        this.principals = principals;
    }

    @GetMapping("/search")
    ResponseEntity<ControlledSearchResult> search(
            @RequestParam("q") String q,
            @RequestParam(value = "types", required = false) String types,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ControlledSearchResult result = searches.search(principals.current(), correlationId, q, types);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(result);
    }
}
