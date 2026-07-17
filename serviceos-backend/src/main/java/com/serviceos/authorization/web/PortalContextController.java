package com.serviceos.authorization.web;

import com.serviceos.authorization.api.MeCapabilitiesView;
import com.serviceos.authorization.api.MeContextsView;
import com.serviceos.authorization.api.MeNavigationView;
import com.serviceos.authorization.api.MeProfileView;
import com.serviceos.authorization.api.PortalContextQueryService;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal `/me*` HTTP 适配器。tenant/actor 只来自受信 CurrentPrincipal；
 * 请求中的 contextId 只能选择服务端已返回上下文，不能创建授权。
 */
@RestController
@RequestMapping("/api/v1")
final class PortalContextController {
    private final PortalContextQueryService queries;
    private final CurrentPrincipalProvider principals;

    PortalContextController(PortalContextQueryService queries, CurrentPrincipalProvider principals) {
        this.queries = queries;
        this.principals = principals;
    }

    @GetMapping("/me")
    ResponseEntity<MeProfileView> me(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.me(principals.current(), correlationId), correlationId);
    }

    @GetMapping("/me/contexts")
    ResponseEntity<MeContextsView> contexts(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.contexts(principals.current(), correlationId), correlationId);
    }

    @GetMapping("/me/capabilities")
    ResponseEntity<MeCapabilitiesView> capabilities(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestParam("contextId") String contextId,
            @RequestParam(value = "expectedContextVersion", required = false) String expectedContextVersion
    ) {
        return response(queries.capabilities(
                principals.current(), correlationId, contextId, expectedContextVersion), correlationId);
    }

    @GetMapping("/me/navigation")
    ResponseEntity<MeNavigationView> navigation(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestParam("contextId") String contextId,
            @RequestParam(value = "expectedContextVersion", required = false) String expectedContextVersion
    ) {
        return response(queries.navigation(
                principals.current(), correlationId, contextId, expectedContextVersion), correlationId);
    }

    private static <T> ResponseEntity<T> response(T body, String correlationId) {
        return ResponseEntity.ok()
                .header("X-Correlation-Id", correlationId)
                .body(body);
    }
}
