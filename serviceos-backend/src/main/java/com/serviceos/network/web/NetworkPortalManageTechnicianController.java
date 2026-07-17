package com.serviceos.network.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.network.api.NetworkPortalManageTechnicianService;
import com.serviceos.network.api.NetworkTechnicianMembershipView;
import com.serviceos.network.api.TechnicianQualificationView;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Network Portal 师傅关系与资质提交 HTTP 适配器。
 * <p>
 * networkId 仅来自可信头 X-Network-Context；不接受请求体中的 networkId。
 */
@RestController
@RequestMapping("/api/v1/network-portal")
final class NetworkPortalManageTechnicianController {
    private final NetworkPortalManageTechnicianService manageTechnicians;
    private final CurrentPrincipalProvider principals;

    NetworkPortalManageTechnicianController(
            NetworkPortalManageTechnicianService manageTechnicians,
            CurrentPrincipalProvider principals
    ) {
        this.manageTechnicians = manageTechnicians;
        this.principals = principals;
    }

    @PostMapping("/technician-memberships")
    ResponseEntity<NetworkTechnicianMembershipView> createMembership(
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody CreatePortalTechnicianMembershipRequest request
    ) {
        NetworkTechnicianMembershipView result = manageTechnicians.createMembership(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                networkContext,
                request.technicianProfileId(),
                request.validFrom());
        return versionedResponse(result, result.version(), correlationId);
    }

    @PostMapping("/technician-memberships/{membershipId}:terminate")
    ResponseEntity<NetworkTechnicianMembershipView> terminateMembership(
            @PathVariable UUID membershipId,
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody TerminatePortalTechnicianMembershipRequest request
    ) {
        NetworkTechnicianMembershipView result = manageTechnicians.terminateMembership(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                networkContext,
                membershipId,
                version(ifMatch),
                request.reason());
        return versionedResponse(result, result.version(), correlationId);
    }

    @PostMapping("/technician-qualifications")
    ResponseEntity<TechnicianQualificationView> submitQualification(
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody SubmitPortalTechnicianQualificationRequest request
    ) {
        TechnicianQualificationView result = manageTechnicians.submitQualification(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                networkContext,
                request.technicianProfileId(),
                request.qualificationCode(),
                request.validFrom(),
                request.validTo());
        return versionedResponse(result, result.version(), correlationId);
    }

    private static long version(String ifMatch) {
        if (ifMatch == null || !ifMatch.matches("\\\"[1-9][0-9]*\\\"")) {
            throw new IllegalArgumentException("If-Match must contain one quoted positive aggregate version");
        }
        return Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1));
    }

    private static <T> ResponseEntity<T> versionedResponse(T body, long version, String correlationId) {
        return ResponseEntity.ok().eTag(Long.toString(version))
                .header(CorrelationIds.HEADER_NAME, correlationId).body(body);
    }

    record CreatePortalTechnicianMembershipRequest(
            @NotNull UUID technicianProfileId,
            @NotNull Instant validFrom
    ) {
    }

    record TerminatePortalTechnicianMembershipRequest(
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    record SubmitPortalTechnicianQualificationRequest(
            @NotNull UUID technicianProfileId,
            @NotBlank @Size(max = 64) String qualificationCode,
            @NotNull Instant validFrom,
            Instant validTo
    ) {
    }
}
