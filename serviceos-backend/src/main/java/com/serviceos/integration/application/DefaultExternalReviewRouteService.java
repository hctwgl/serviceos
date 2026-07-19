package com.serviceos.integration.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.evidence.api.ReviewCaseService;
import com.serviceos.evidence.api.ReviewCaseView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.integration.api.ExternalReviewRouteService;
import com.serviceos.integration.api.ExternalReviewRouteView;
import com.serviceos.integration.api.RegisterExternalReviewRouteCommand;
import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * 已确认提审成功后登记唯一回调路由；不从订单号猜测 ReviewCase。
 *
 * <p>connectorVersion 由回调 mappingVersion 经 Profile 注册表唯一解析，禁止本类硬编码车企常量。</p>
 */
@Service
final class DefaultExternalReviewRouteService implements ExternalReviewRouteService {
    private static final String CAPABILITY = "integration.registerExternalReviewRoute";
    private static final String OPERATION = "integration.externalReviewRoute.register";

    private final InboundMessageRepository messages;
    private final ReviewCaseService reviews;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final OutboundReviewSubmissionProfiles profiles;

    DefaultExternalReviewRouteService(
            InboundMessageRepository messages,
            ReviewCaseService reviews,
            AuthorizationService authorization,
            IdempotencyService idempotency,
            AuditAppender audit,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock,
            OutboundReviewSubmissionProfiles profiles
    ) {
        this.messages = messages;
        this.reviews = reviews;
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.audit = audit;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.profiles = profiles;
    }

    @Override
    @Transactional
    public ExternalReviewRouteView register(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            RegisterExternalReviewRouteCommand command
    ) {
        if (principal.principalType() != CurrentPrincipal.PrincipalType.SERVICE) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED,
                    "External review route requires a SERVICE principal");
        }
        if (command.reviewCaseId() == null) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "reviewCaseId is required");
        }
        ReviewCaseView reviewCase = reviews.get(principal, metadata.correlationId(), command.reviewCaseId());
        AuthorizationDecision auth = authorization.require(principal,
                AuthorizationRequest.projectCapability(
                        CAPABILITY, principal.tenantId(), "ExternalReviewRoute",
                        command.reviewCaseId().toString(), reviewCase.projectId().toString()),
                metadata.correlationId());
        if (!"CLIENT".equals(reviewCase.origin()) || !"OPEN".equals(reviewCase.status())) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_STATE_CONFLICT,
                    "External review route requires an OPEN CLIENT ReviewCase");
        }

        String orderCode = text(command.externalOrderCode(), "externalOrderCode", 50);
        String submissionRef = text(command.externalSubmissionRef(), "externalSubmissionRef", 160);
        String batchRef = text(command.callbackBatchRef(), "callbackBatchRef", 160);
        String mappingVersion = text(command.mappingVersionId(), "mappingVersionId", 120);
        if (!submissionRef.equals(reviewCase.externalSubmissionRef())
                || !batchRef.equals(reviewCase.callbackBatchRef())
                || !mappingVersion.equals(reviewCase.mappingVersionId())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "External review route must match the CLIENT ReviewCase frozen lineage");
        }

        var profile = profiles.requireForRouteRegistration(mappingVersion);
        String connectorVersion = profile.identity().connectorVersionId();
        String requestDigest = Sha256.digest(
                connectorVersion + "|" + orderCode + "|" + reviewCase.reviewCaseId() + "|"
                        + submissionRef + "|" + batchRef + "|" + mappingVersion);
        CommandContext context = new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        IdempotencyDecision decision = idempotency.begin(context, OPERATION, requestDigest);
        if (decision.kind() == IdempotencyDecision.Kind.REPLAY) {
            UUID routeId = decision.resourceId().map(UUID::fromString)
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.INTERNAL_ERROR, "External review route replay id missing"));
            return messages.findExternalReviewRoute(principal.tenantId(), routeId)
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.INTERNAL_ERROR, "External review route replay result missing"));
        }

        Instant now = clock.instant();
        var registration = messages.registerExternalReviewRoute(
                new InboundMessageRepository.NewExternalReviewRoute(
                        UUID.randomUUID(), principal.tenantId(), reviewCase.projectId(), connectorVersion,
                        orderCode, reviewCase.reviewCaseId(), submissionRef, batchRef, mappingVersion,
                        principal.principalId(), now));
        ExternalReviewRouteView route = registration.route();
        if (!sameRoute(route, reviewCase, orderCode, submissionRef, batchRef, mappingVersion)) {
            throw new BusinessProblem(ProblemCode.REVIEW_CASE_CONFLICT,
                    "External order already has a different active review route");
        }

        if (registration.created()) {
            String payload = json(new RouteRegisteredPayload(
                    route.reviewRouteId(), route.projectId(), route.externalOrderCode(), route.reviewCaseId(),
                    route.externalSubmissionRef(), route.callbackBatchRef(), route.mappingVersionId(), now));
            outbox.append(new OutboxEvent(
                    UUID.randomUUID(), UUID.randomUUID(), "integration",
                    "integration.external-review-route-registered", 1,
                    "ExternalReviewRoute", route.reviewRouteId().toString(), 1L,
                    principal.tenantId(), metadata.correlationId(), metadata.idempotencyKey(),
                    route.reviewCaseId().toString(), payload, Sha256.digest(payload), now));
            audit.append(new AuditEntry(
                    UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                    "EXTERNAL_REVIEW_ROUTE_REGISTERED", CAPABILITY, "ExternalReviewRoute",
                    route.reviewRouteId().toString(), "ALLOW", auth.matchedGrantIds(), auth.policyVersion(),
                    "ACTIVE", null, requestDigest, metadata.correlationId(), now));
        }
        idempotency.complete(context, OPERATION, route.reviewRouteId().toString(), Sha256.digest(json(route)));
        return route;
    }

    private static boolean sameRoute(
            ExternalReviewRouteView route,
            ReviewCaseView reviewCase,
            String orderCode,
            String submissionRef,
            String batchRef,
            String mappingVersion
    ) {
        return route.reviewCaseId().equals(reviewCase.reviewCaseId())
                && route.projectId().equals(reviewCase.projectId())
                && route.externalOrderCode().equals(orderCode)
                && route.externalSubmissionRef().equals(submissionRef)
                && route.callbackBatchRef().equals(batchRef)
                && route.mappingVersionId().equals(mappingVersion)
                && "ACTIVE".equals(route.status());
    }

    private static String text(String value, String field, int maximum) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > maximum) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " is invalid");
        }
        return value;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("External review route serialization failed", exception);
        }
    }

    private record RouteRegisteredPayload(
            UUID reviewRouteId,
            UUID projectId,
            String externalOrderCode,
            UUID reviewCaseId,
            String externalSubmissionRef,
            String callbackBatchRef,
            String mappingVersionId,
            Instant registeredAt
    ) {
    }
}
