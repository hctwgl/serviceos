package com.serviceos.network.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.network.api.ClearanceWorkItemPage;
import com.serviceos.network.api.DeactivationImpactView;
import com.serviceos.network.api.EligibilityView;
import com.serviceos.network.api.NetworkCommandService;
import com.serviceos.network.api.NetworkMembershipPage;
import com.serviceos.network.api.NetworkMembershipView;
import com.serviceos.network.api.NetworkQueryService;
import com.serviceos.network.api.NetworkTechnicianMembershipPage;
import com.serviceos.network.api.NetworkTechnicianMembershipView;
import com.serviceos.network.api.PartnerOrganizationPage;
import com.serviceos.network.api.PartnerOrganizationView;
import com.serviceos.network.api.ServiceNetworkPage;
import com.serviceos.network.api.ServiceNetworkView;
import com.serviceos.network.api.TechnicianProfilePage;
import com.serviceos.network.api.TechnicianProfileView;
import com.serviceos.network.api.TechnicianQualificationPage;
import com.serviceos.network.api.TechnicianQualificationView;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 网点与师傅目录 HTTP 适配器；tenant/actor 只来自受信 CurrentPrincipal。 */
@RestController
@RequestMapping("/api/v1")
final class NetworkController {
    private final NetworkQueryService queries;
    private final NetworkCommandService commands;
    private final CurrentPrincipalProvider principals;

    NetworkController(
            NetworkQueryService queries,
            NetworkCommandService commands,
            CurrentPrincipalProvider principals
    ) {
        this.queries = queries;
        this.commands = commands;
        this.principals = principals;
    }

    @PostMapping("/partner-organizations")
    ResponseEntity<PartnerOrganizationView> createPartnerOrganization(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody CreatePartnerRequest request
    ) {
        PartnerOrganizationView result = commands.createPartnerOrganization(principals.current(),
                metadata(correlationId, idempotencyKey), request.code(), request.name());
        return versionedResponse(result, result.version(), correlationId);
    }

    @GetMapping("/partner-organizations")
    ResponseEntity<PartnerOrganizationPage> listPartnerOrganizations(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listPartnerOrganizations(principals.current(), correlationId), correlationId);
    }

    @GetMapping("/partner-organizations/{partnerOrganizationId}")
    ResponseEntity<PartnerOrganizationView> getPartnerOrganization(
            @PathVariable UUID partnerOrganizationId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        PartnerOrganizationView result = queries.getPartnerOrganization(
                principals.current(), correlationId, partnerOrganizationId);
        return versionedResponse(result, result.version(), correlationId);
    }

    @PostMapping("/service-networks")
    ResponseEntity<ServiceNetworkView> createServiceNetwork(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody CreateServiceNetworkRequest request
    ) {
        ServiceNetworkView result = commands.createServiceNetwork(principals.current(),
                metadata(correlationId, idempotencyKey), request.partnerOrganizationId(),
                request.networkCode(), request.networkName());
        return versionedResponse(result, result.version(), correlationId);
    }

    @GetMapping("/service-networks")
    ResponseEntity<ServiceNetworkPage> listServiceNetworks(
            @RequestParam(required = false) UUID partnerOrganizationId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listServiceNetworks(principals.current(), correlationId, partnerOrganizationId),
                correlationId);
    }

    @GetMapping("/service-networks/{networkId}")
    ResponseEntity<ServiceNetworkView> getServiceNetwork(
            @PathVariable UUID networkId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ServiceNetworkView result = queries.getServiceNetwork(principals.current(), correlationId, networkId);
        return versionedResponse(result, result.version(), correlationId);
    }

    @PostMapping("/service-networks/{networkId}:deactivate")
    ResponseEntity<ServiceNetworkView> deactivateServiceNetwork(
            @PathVariable UUID networkId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody DeactivateRequest request
    ) {
        ServiceNetworkView result = commands.deactivateServiceNetwork(principals.current(),
                metadata(correlationId, idempotencyKey), networkId, version(ifMatch), request.reason());
        return versionedResponse(result, result.version(), correlationId);
    }

    @GetMapping("/service-networks/{networkId}/memberships")
    ResponseEntity<NetworkMembershipPage> listNetworkMemberships(
            @PathVariable UUID networkId,
            @RequestParam(required = false) UUID principalId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listNetworkMemberships(principals.current(), correlationId, networkId, principalId),
                correlationId);
    }

    @PostMapping("/service-networks/{networkId}/memberships")
    ResponseEntity<NetworkMembershipView> inviteNetworkMember(
            @PathVariable UUID networkId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody InviteMemberRequest request
    ) {
        NetworkMembershipView result = commands.inviteNetworkMember(principals.current(),
                metadata(correlationId, idempotencyKey), networkId,
                ifMatch == null ? null : version(ifMatch), request.principalId(),
                request.role(), request.validFrom());
        return versionedResponse(result, result.version(), correlationId);
    }

    @PostMapping("/network-memberships/{membershipId}:terminate")
    ResponseEntity<NetworkMembershipView> terminateNetworkMembership(
            @PathVariable UUID membershipId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody TerminateRequest request
    ) {
        NetworkMembershipView result = commands.terminateNetworkMembership(principals.current(),
                metadata(correlationId, idempotencyKey), membershipId, version(ifMatch), request.reason());
        return versionedResponse(result, result.version(), correlationId);
    }

    @PostMapping("/technician-profiles")
    ResponseEntity<TechnicianProfileView> createTechnicianProfile(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody CreateTechnicianRequest request
    ) {
        TechnicianProfileView result = commands.createTechnicianProfile(principals.current(),
                metadata(correlationId, idempotencyKey), request.principalId(), request.displayName(),
                request.supportedClientKinds());
        return versionedResponse(result, result.version(), correlationId);
    }

    @PostMapping("/technician-profiles/{profileId}:declare-supported-client-kinds")
    ResponseEntity<TechnicianProfileView> declareTechnicianSupportedClientKinds(
            @PathVariable UUID profileId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody DeclareTechnicianClientKindsRequest request
    ) {
        TechnicianProfileView result = commands.declareTechnicianSupportedClientKinds(
                principals.current(), metadata(correlationId, idempotencyKey),
                profileId, version(ifMatch), request.supportedClientKinds());
        return versionedResponse(result, result.version(), correlationId);
    }

    @GetMapping("/technician-profiles")
    ResponseEntity<TechnicianProfilePage> listTechnicianProfiles(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listTechnicianProfiles(principals.current(), correlationId), correlationId);
    }

    @GetMapping("/technician-profiles/{profileId}")
    ResponseEntity<TechnicianProfileView> getTechnicianProfile(
            @PathVariable UUID profileId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        TechnicianProfileView result = queries.getTechnicianProfile(principals.current(), correlationId, profileId);
        return versionedResponse(result, result.version(), correlationId);
    }

    @PostMapping("/technician-profiles/{profileId}:disable")
    ResponseEntity<TechnicianProfileView> disableTechnicianProfile(
            @PathVariable UUID profileId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody DisableTechnicianRequest request
    ) {
        TechnicianProfileView result = commands.disableTechnicianProfile(principals.current(),
                metadata(correlationId, idempotencyKey), profileId, version(ifMatch), request.reason());
        return versionedResponse(result, result.version(), correlationId);
    }

    @PostMapping("/technician-profiles/{profileId}:enable")
    ResponseEntity<TechnicianProfileView> enableTechnicianProfile(
            @PathVariable UUID profileId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        TechnicianProfileView result = commands.enableTechnicianProfile(principals.current(),
                metadata(correlationId, idempotencyKey), profileId, version(ifMatch));
        return versionedResponse(result, result.version(), correlationId);
    }

    @GetMapping("/technician-profiles/{profileId}/eligibility")
    ResponseEntity<EligibilityView> checkEligibility(
            @PathVariable UUID profileId,
            @RequestParam UUID networkId,
            @RequestParam(required = false) Instant at,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.checkEligibility(principals.current(), correlationId, profileId, networkId, at),
                correlationId);
    }

    @PostMapping("/network-technician-memberships")
    ResponseEntity<NetworkTechnicianMembershipView> createNetworkTechnicianMembership(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody CreateTechnicianMembershipRequest request
    ) {
        NetworkTechnicianMembershipView result = commands.createNetworkTechnicianMembership(principals.current(),
                metadata(correlationId, idempotencyKey), request.networkId(),
                request.technicianProfileId(), request.validFrom());
        return versionedResponse(result, result.version(), correlationId);
    }

    @GetMapping("/network-technician-memberships")
    ResponseEntity<NetworkTechnicianMembershipPage> listNetworkTechnicianMemberships(
            @RequestParam(required = false) UUID networkId,
            @RequestParam(required = false) UUID technicianProfileId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listNetworkTechnicianMemberships(
                principals.current(), correlationId, networkId, technicianProfileId), correlationId);
    }

    @PostMapping("/network-technician-memberships/{membershipId}:terminate")
    ResponseEntity<NetworkTechnicianMembershipView> terminateNetworkTechnicianMembership(
            @PathVariable UUID membershipId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody TerminateRequest request
    ) {
        NetworkTechnicianMembershipView result = commands.terminateNetworkTechnicianMembership(principals.current(),
                metadata(correlationId, idempotencyKey), membershipId, version(ifMatch), request.reason());
        return versionedResponse(result, result.version(), correlationId);
    }

    @PostMapping("/technician-qualifications")
    ResponseEntity<TechnicianQualificationView> submitQualification(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody SubmitQualificationRequest request
    ) {
        TechnicianQualificationView result = commands.submitQualification(principals.current(),
                metadata(correlationId, idempotencyKey), request.technicianProfileId(),
                request.qualificationCode(), request.validFrom(), request.validTo());
        return versionedResponse(result, result.version(), correlationId);
    }

    @GetMapping("/technician-profiles/{profileId}/qualifications")
    ResponseEntity<TechnicianQualificationPage> listTechnicianQualifications(
            @PathVariable UUID profileId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listTechnicianQualifications(principals.current(), correlationId, profileId),
                correlationId);
    }

    @PostMapping("/technician-qualifications/{qualificationId}:decide")
    ResponseEntity<TechnicianQualificationView> decideQualification(
            @PathVariable UUID qualificationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody DecideQualificationRequest request
    ) {
        TechnicianQualificationView result = commands.decideQualification(principals.current(),
                metadata(correlationId, idempotencyKey), qualificationId, version(ifMatch),
                request.decision(), request.reason());
        return versionedResponse(result, result.version(), correlationId);
    }

    @GetMapping("/clearance-work-items")
    ResponseEntity<ClearanceWorkItemPage> listClearanceWorkItems(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listOpenClearanceWorkItems(principals.current(), correlationId), correlationId);
    }

    @GetMapping("/service-networks/{networkId}/deactivation-impact")
    ResponseEntity<DeactivationImpactView> getNetworkDeactivationImpact(
            @PathVariable UUID networkId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.getNetworkDeactivationImpact(principals.current(), correlationId, networkId),
                correlationId);
    }

    private static CommandMetadata metadata(String correlationId, String idempotencyKey) {
        return new CommandMetadata(correlationId, idempotencyKey);
    }

    private static long version(String ifMatch) {
        if (ifMatch == null || !ifMatch.matches("\\\"[1-9][0-9]*\\\"")) {
            throw new IllegalArgumentException("If-Match must contain one quoted positive aggregate version");
        }
        return Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1));
    }

    private static <T> ResponseEntity<T> response(T body, String correlationId) {
        return ResponseEntity.ok().header(CorrelationIds.HEADER_NAME, correlationId).body(body);
    }

    private static <T> ResponseEntity<T> versionedResponse(T body, long version, String correlationId) {
        return ResponseEntity.ok().eTag(Long.toString(version))
                .header(CorrelationIds.HEADER_NAME, correlationId).body(body);
    }
}

record CreatePartnerRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name
) {}

record CreateServiceNetworkRequest(
        @NotNull UUID partnerOrganizationId,
        @NotBlank @Size(max = 64) String networkCode,
        @NotBlank @Size(max = 200) String networkName
) {}

record InviteMemberRequest(
        @NotNull UUID principalId,
        @NotBlank @Size(max = 40) String role,
        @NotNull Instant validFrom
) {}

record TerminateRequest(@NotBlank @Size(max = 500) String reason) {}

record DeactivateRequest(@NotBlank @Size(max = 500) String reason) {}

record CreateTechnicianRequest(
        @NotNull UUID principalId,
        @NotBlank @Size(max = 200) String displayName,
        List<String> supportedClientKinds
) {}

record DeclareTechnicianClientKindsRequest(List<String> supportedClientKinds) {}

record DisableTechnicianRequest(@NotBlank @Size(max = 500) String reason) {}

record CreateTechnicianMembershipRequest(
        @NotNull UUID networkId,
        @NotNull UUID technicianProfileId,
        @NotNull Instant validFrom
) {}

record SubmitQualificationRequest(
        @NotNull UUID technicianProfileId,
        @NotBlank @Size(max = 64) String qualificationCode,
        @NotNull Instant validFrom,
        Instant validTo
) {}

record DecideQualificationRequest(
        @NotBlank @Size(max = 24) String decision,
        @Size(max = 500) String reason
) {}
