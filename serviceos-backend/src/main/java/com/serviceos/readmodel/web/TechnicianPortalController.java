package com.serviceos.readmodel.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.TechnicianPortalFeedPage;
import com.serviceos.readmodel.api.TechnicianPortalQueryService;
import com.serviceos.readmodel.api.TechnicianPortalSchedulePage;
import com.serviceos.readmodel.api.TechnicianPortalSyncSummary;
import com.serviceos.readmodel.api.TechnicianPortalTaskDetail;
import com.serviceos.shared.ClientMetadata;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Technician Portal Feed HTTP 适配器。networkId 仅来自可信头 X-Technician-Context。
 */
@RestController
@RequestMapping("/api/v1/technician/me")
final class TechnicianPortalController {
    private final TechnicianPortalQueryService queries;
    private final CurrentPrincipalProvider principals;

    TechnicianPortalController(TechnicianPortalQueryService queries, CurrentPrincipalProvider principals) {
        this.queries = queries;
        this.principals = principals;
    }

    @GetMapping("/task-feed")
    ResponseEntity<TechnicianPortalFeedPage> taskFeed(
            @RequestHeader(value = "X-Technician-Context", required = false) String technicianContext,
            @RequestParam(value = "sinceCursor", required = false) String sinceCursor,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestAttribute(value = ClientMetadata.KIND_ATTRIBUTE, required = false) String clientKind
    ) {
        TechnicianPortalFeedPage body = queries.taskFeed(
                principals.current(), correlationId, technicianContext, clientKind, sinceCursor);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(body);
    }

    @GetMapping("/schedule")
    ResponseEntity<TechnicianPortalSchedulePage> schedule(
            @RequestHeader(value = "X-Technician-Context", required = false) String technicianContext,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        TechnicianPortalSchedulePage body = queries.schedule(
                principals.current(), correlationId, technicianContext);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(body);
    }

    @GetMapping("/sync-summary")
    ResponseEntity<TechnicianPortalSyncSummary> syncSummary(
            @RequestHeader(value = "X-Technician-Context", required = false) String technicianContext,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        TechnicianPortalSyncSummary body = queries.syncSummary(
                principals.current(), correlationId, technicianContext);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(body);
    }

    @GetMapping("/tasks/{taskId}")
    ResponseEntity<TechnicianPortalTaskDetail> taskDetail(
            @PathVariable UUID taskId,
            @RequestHeader(value = "X-Technician-Context", required = false) String technicianContext,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestAttribute(value = ClientMetadata.KIND_ATTRIBUTE, required = false) String clientKind
    ) {
        TechnicianPortalTaskDetail body = queries.taskDetail(
                principals.current(), correlationId, technicianContext, clientKind, taskId);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(body);
    }
}
