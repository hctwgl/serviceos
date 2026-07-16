package com.serviceos.evidence.web;

import com.serviceos.evidence.api.CorrectionCaseQueryService;
import com.serviceos.evidence.api.CorrectionCaseQueuePage;
import com.serviceos.evidence.api.CorrectionCaseQueueQuery;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/correction-cases")
final class CorrectionCaseQueryController {
    private final CorrectionCaseQueryService corrections;
    private final CurrentPrincipalProvider principals;

    CorrectionCaseQueryController(
            CorrectionCaseQueryService corrections, CurrentPrincipalProvider principals
    ) {
        this.corrections = corrections;
        this.principals = principals;
    }

    @GetMapping
    ResponseEntity<CorrectionCaseQueuePage> list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID taskId,
            @RequestParam(required = false) UUID sourceReviewCaseId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        CorrectionCaseQueuePage page = corrections.list(
                principals.current(),
                correlationId,
                new CorrectionCaseQueueQuery(
                        projectId, status, taskId, sourceReviewCaseId, cursor, limit));
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(page);
    }
}
