package com.serviceos.readmodel.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.NetworkPortalCapacityItem;
import com.serviceos.readmodel.api.NetworkPortalPage;
import com.serviceos.readmodel.api.NetworkPortalQueryService;
import com.serviceos.readmodel.api.NetworkPortalTaskItem;
import com.serviceos.readmodel.api.NetworkPortalTechnicianItem;
import com.serviceos.readmodel.api.NetworkPortalWorkbenchView;
import com.serviceos.readmodel.api.NetworkPortalWorkOrderItem;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Network Portal 只读 HTTP 适配器。networkId 仅来自可信头 X-Network-Context，
 * 不接受查询参数任意指定。
 */
@RestController
@RequestMapping("/api/v1/network-portal")
final class NetworkPortalController {
    private final NetworkPortalQueryService queries;
    private final CurrentPrincipalProvider principals;

    NetworkPortalController(NetworkPortalQueryService queries, CurrentPrincipalProvider principals) {
        this.queries = queries;
        this.principals = principals;
    }

    @GetMapping("/work-orders")
    ResponseEntity<NetworkPortalPage<NetworkPortalWorkOrderItem>> workOrders(
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listWorkOrders(principals.current(), correlationId, networkContext), correlationId);
    }

    @GetMapping("/tasks")
    ResponseEntity<NetworkPortalPage<NetworkPortalTaskItem>> tasks(
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listTasks(principals.current(), correlationId, networkContext), correlationId);
    }

    @GetMapping("/technicians")
    ResponseEntity<NetworkPortalPage<NetworkPortalTechnicianItem>> technicians(
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listTechnicians(principals.current(), correlationId, networkContext), correlationId);
    }

    @GetMapping("/capacity")
    ResponseEntity<NetworkPortalPage<NetworkPortalCapacityItem>> capacity(
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listCapacity(principals.current(), correlationId, networkContext), correlationId);
    }

    @GetMapping("/workbench")
    ResponseEntity<NetworkPortalWorkbenchView> workbench(
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        NetworkPortalWorkbenchView body = queries.workbench(principals.current(), correlationId, networkContext);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(body);
    }

    private static <T> ResponseEntity<NetworkPortalPage<T>> response(
            NetworkPortalPage<T> body, String correlationId
    ) {
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(body);
    }
}
