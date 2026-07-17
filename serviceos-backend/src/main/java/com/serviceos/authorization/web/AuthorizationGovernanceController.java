package com.serviceos.authorization.web;

import com.serviceos.authorization.api.AuthorizationExplainResult;
import com.serviceos.authorization.api.AuthorizationGovernanceCommandService;
import com.serviceos.authorization.api.AuthorizationGovernanceQueryService;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.CapabilityView;
import com.serviceos.authorization.api.DelegationPage;
import com.serviceos.authorization.api.DelegationView;
import com.serviceos.authorization.api.RoleGrantPage;
import com.serviceos.authorization.api.RoleGrantView;
import com.serviceos.authorization.api.RolePage;
import com.serviceos.authorization.api.RoleView;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

/** 角色与授权治理 HTTP 适配器；tenant/actor 只来自受信 CurrentPrincipal。 */
@RestController
@RequestMapping("/api/v1")
final class AuthorizationGovernanceController {
    private final AuthorizationGovernanceQueryService queries;
    private final AuthorizationGovernanceCommandService commands;
    private final CurrentPrincipalProvider principals;

    AuthorizationGovernanceController(
            AuthorizationGovernanceQueryService queries,
            AuthorizationGovernanceCommandService commands,
            CurrentPrincipalProvider principals
    ) {
        this.queries = queries;
        this.commands = commands;
        this.principals = principals;
    }

    @GetMapping("/capabilities")
    ResponseEntity<List<CapabilityView>> listCapabilities(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listCapabilities(principals.current(), correlationId), correlationId);
    }

    @GetMapping("/roles")
    ResponseEntity<RolePage> listRoles(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listRoles(principals.current(), correlationId), correlationId);
    }

    @PostMapping("/roles")
    ResponseEntity<RoleView> createRole(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody CreateRoleRequest request
    ) {
        RoleView result = commands.createRole(principals.current(), metadata(correlationId, idempotencyKey),
                request.roleCode(), request.roleName(), request.description(), request.capabilityCodes());
        return versionedResponse(result, result.version(), correlationId);
    }

    @GetMapping("/roles/{roleId}")
    ResponseEntity<RoleView> getRole(
            @PathVariable UUID roleId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        RoleView result = queries.getRole(principals.current(), correlationId, roleId);
        return versionedResponse(result, result.version(), correlationId);
    }

    @GetMapping("/role-grants")
    ResponseEntity<RoleGrantPage> listRoleGrants(
            @RequestParam(required = false) String principalId,
            @RequestParam(required = false) String grantStatus,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listRoleGrants(principals.current(), correlationId, principalId, grantStatus),
                correlationId);
    }

    @PostMapping("/role-grants")
    ResponseEntity<RoleGrantView> requestRoleGrant(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody RequestRoleGrantRequest request
    ) {
        RoleGrantView result = commands.requestRoleGrant(principals.current(),
                metadata(correlationId, idempotencyKey), request.principalId(), request.roleId(),
                request.scopeType(), request.scopeRef(), request.grantEffect(),
                request.validFrom(), request.validTo(), request.requestReason());
        return versionedResponse(result, result.version(), correlationId);
    }

    @PostMapping("/role-grants/{grantId}:approve")
    ResponseEntity<RoleGrantView> decideRoleGrant(
            @PathVariable UUID grantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody DecideRoleGrantRequest request
    ) {
        RoleGrantView result = commands.decideRoleGrant(principals.current(),
                metadata(correlationId, idempotencyKey), grantId, version(ifMatch),
                request.decision(), request.note());
        return versionedResponse(result, result.version(), correlationId);
    }

    @PostMapping("/role-grants/{grantId}:revoke")
    ResponseEntity<RoleGrantView> revokeRoleGrant(
            @PathVariable UUID grantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody RevokeRequest request
    ) {
        RoleGrantView result = commands.revokeRoleGrant(principals.current(),
                metadata(correlationId, idempotencyKey), grantId, version(ifMatch), request.reason());
        return versionedResponse(result, result.version(), correlationId);
    }

    @GetMapping("/delegations")
    ResponseEntity<DelegationPage> listDelegations(
            @RequestParam(required = false) String delegatePrincipalId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listDelegations(principals.current(), correlationId, delegatePrincipalId),
                correlationId);
    }

    @PostMapping("/delegations")
    ResponseEntity<DelegationView> createDelegation(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody CreateDelegationRequest request
    ) {
        DelegationView result = commands.createDelegation(principals.current(),
                metadata(correlationId, idempotencyKey), request.delegatePrincipalId(),
                request.capabilityCodes(), request.scopeType(), request.scopeRef(),
                request.validFrom(), request.validTo(), request.reason());
        return versionedResponse(result, result.version(), correlationId);
    }

    @PostMapping("/delegations/{delegationId}:revoke")
    ResponseEntity<DelegationView> revokeDelegation(
            @PathVariable UUID delegationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody RevokeRequest request
    ) {
        DelegationView result = commands.revokeDelegation(principals.current(),
                metadata(correlationId, idempotencyKey), delegationId, version(ifMatch), request.reason());
        return versionedResponse(result, result.version(), correlationId);
    }

    @PostMapping("/authorization:explain")
    ResponseEntity<AuthorizationExplainResult> explain(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody ExplainRequest request
    ) {
        AuthorizationRequest authRequest = new AuthorizationRequest(
                request.capability(), principals.current().tenantId(), request.resourceType(),
                request.resourceId(), request.projectId(), request.organizationId(),
                request.regionCode(), request.networkId());
        return response(commands.explain(principals.current(), correlationId,
                request.subjectPrincipalId(), authRequest), correlationId);
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

record CreateRoleRequest(
        @NotBlank @Size(max = 120) String roleCode,
        @NotBlank @Size(max = 200) String roleName,
        @Size(max = 500) String description,
        @NotEmpty List<@NotBlank @Size(max = 120) String> capabilityCodes
) {
}

record RequestRoleGrantRequest(
        @NotBlank @Size(max = 128) String principalId,
        @NotNull UUID roleId,
        @NotBlank @Size(max = 32) String scopeType,
        @NotBlank @Size(max = 128) String scopeRef,
        @Size(max = 16) String grantEffect,
        @NotNull Instant validFrom,
        Instant validTo,
        @NotBlank @Size(max = 500) String requestReason
) {
}

record DecideRoleGrantRequest(
        @NotBlank @Size(max = 16) String decision,
        @Size(max = 500) String note
) {
}

record CreateDelegationRequest(
        @NotBlank @Size(max = 128) String delegatePrincipalId,
        @NotEmpty List<@NotBlank @Size(max = 120) String> capabilityCodes,
        @NotBlank @Size(max = 32) String scopeType,
        @NotBlank @Size(max = 128) String scopeRef,
        @NotNull Instant validFrom,
        Instant validTo,
        @NotBlank @Size(max = 500) String reason
) {
}

record RevokeRequest(@NotBlank @Size(max = 500) String reason) {
}

record ExplainRequest(
        @NotBlank @Size(max = 128) String subjectPrincipalId,
        @NotBlank @Size(max = 120) String capability,
        @NotBlank @Size(max = 64) String resourceType,
        @NotBlank @Size(max = 128) String resourceId,
        @Size(max = 128) String projectId,
        @Size(max = 128) String organizationId,
        @Size(max = 128) String regionCode,
        @Size(max = 128) String networkId
) {
}
