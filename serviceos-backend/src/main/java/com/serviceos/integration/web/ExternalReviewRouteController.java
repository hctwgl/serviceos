package com.serviceos.integration.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.integration.api.ExternalReviewRouteService;
import com.serviceos.integration.api.ExternalReviewRouteView;
import com.serviceos.integration.api.RegisterExternalReviewRouteCommand;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 已确认车企提审成功后的内部路由登记入口；外部 CPIM 不可调用。 */
@RestController
@RequestMapping("/api/v1/internal/integration/byd/review-routes")
final class ExternalReviewRouteController {
    private final ExternalReviewRouteService routes;
    private final CurrentPrincipalProvider principals;

    ExternalReviewRouteController(ExternalReviewRouteService routes, CurrentPrincipalProvider principals) {
        this.routes = routes;
        this.principals = principals;
    }

    @PostMapping
    ResponseEntity<ExternalReviewRouteView> register(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody RegisterRouteRequest request
    ) {
        ExternalReviewRouteView route = routes.register(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey),
                new RegisterExternalReviewRouteCommand(
                        request.externalOrderCode(), request.reviewCaseId(),
                        request.externalSubmissionRef(), request.callbackBatchRef(),
                        request.mappingVersionId()));
        return ResponseEntity.status(201).body(route);
    }

    record RegisterRouteRequest(
            @NotBlank @Size(max = 50) String externalOrderCode,
            @NotNull UUID reviewCaseId,
            @NotBlank @Size(max = 160) String externalSubmissionRef,
            @NotBlank @Size(max = 160) String callbackBatchRef,
            @NotBlank @Size(max = 120) String mappingVersionId
    ) {
    }
}
