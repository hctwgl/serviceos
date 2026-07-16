package com.serviceos.evidence.web;

import com.serviceos.evidence.api.ReviewCaseQueryService;
import com.serviceos.evidence.api.ReviewCaseQueuePage;
import com.serviceos.evidence.api.ReviewCaseQueueQuery;
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
@RequestMapping("/api/v1/review-cases")
final class ReviewCaseQueryController {
    private final ReviewCaseQueryService reviews;
    private final CurrentPrincipalProvider principals;

    ReviewCaseQueryController(
            ReviewCaseQueryService reviews, CurrentPrincipalProvider principals
    ) {
        this.reviews = reviews;
        this.principals = principals;
    }

    @GetMapping
    ResponseEntity<ReviewCaseQueuePage> list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) UUID taskId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ReviewCaseQueuePage page = reviews.list(
                principals.current(),
                correlationId,
                new ReviewCaseQueueQuery(projectId, status, origin, taskId, cursor, limit));
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(page);
    }
}
