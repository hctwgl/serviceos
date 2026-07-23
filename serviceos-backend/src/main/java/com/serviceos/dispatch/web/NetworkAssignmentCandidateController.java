package com.serviceos.dispatch.web;

import com.serviceos.dispatch.api.NetworkAssignmentCandidateQuery;
import com.serviceos.dispatch.api.NetworkAssignmentCandidateView;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Admin 责任网点候选 HTTP 适配器。 */
@RestController
@RequestMapping("/api/v1/tasks")
final class NetworkAssignmentCandidateController {
    private final NetworkAssignmentCandidateQuery candidates;
    private final CurrentPrincipalProvider principals;

    NetworkAssignmentCandidateController(
            NetworkAssignmentCandidateQuery candidates,
            CurrentPrincipalProvider principals
    ) {
        this.candidates = candidates;
        this.principals = principals;
    }

    @GetMapping("/{taskId}/network-assignment-candidates")
    ResponseEntity<NetworkAssignmentCandidateView> findCandidates(
            @PathVariable UUID taskId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        NetworkAssignmentCandidateView result = candidates.findCandidates(
                principals.current(), correlationId, taskId);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(result);
    }
}
