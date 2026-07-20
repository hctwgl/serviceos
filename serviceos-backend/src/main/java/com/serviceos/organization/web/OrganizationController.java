package com.serviceos.organization.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.organization.api.DirectorySyncBatchView;
import com.serviceos.organization.api.OrgMembershipPage;
import com.serviceos.organization.api.OrgMembershipSummaryPage;
import com.serviceos.organization.api.OrgMembershipView;
import com.serviceos.organization.api.OrgUnitView;
import com.serviceos.organization.api.OrganizationCommandService;
import com.serviceos.organization.api.OrganizationCommandService.SyncItemInput;
import com.serviceos.organization.api.OrganizationDetail;
import com.serviceos.organization.api.OrganizationPage;
import com.serviceos.organization.api.OrganizationQueryService;
import com.serviceos.organization.api.OrganizationView;
import com.serviceos.organization.api.ReassignmentWorkItemPage;
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

/** 企业组织目录 HTTP 适配器；tenant/actor 只来自受信 CurrentPrincipal。 */
@RestController
@RequestMapping("/api/v1")
final class OrganizationController {
    private final OrganizationQueryService queries;
    private final OrganizationCommandService commands;
    private final CurrentPrincipalProvider principals;

    OrganizationController(
            OrganizationQueryService queries,
            OrganizationCommandService commands,
            CurrentPrincipalProvider principals
    ) {
        this.queries = queries;
        this.commands = commands;
        this.principals = principals;
    }

    @PostMapping("/organizations")
    ResponseEntity<OrganizationView> createOrganization(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody CreateOrganizationRequest request
    ) {
        OrganizationView result = commands.createOrganization(principals.current(),
                metadata(correlationId, idempotencyKey), request.code(), request.name(),
                request.authorityMode(), request.sourceSystem(), request.sourceKey());
        return ResponseEntity.ok().eTag(Long.toString(result.version()))
                .header(CorrelationIds.HEADER_NAME, correlationId).body(result);
    }

    @GetMapping("/organizations")
    ResponseEntity<OrganizationPage> listOrganizations(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listOrganizations(principals.current(), correlationId), correlationId);
    }

    @GetMapping("/organizations/{organizationId}")
    ResponseEntity<OrganizationDetail> getOrganization(
            @PathVariable UUID organizationId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        OrganizationDetail detail = queries.getOrganization(principals.current(), correlationId, organizationId);
        return ResponseEntity.ok().eTag(Long.toString(detail.organization().version()))
                .header(CorrelationIds.HEADER_NAME, correlationId).body(detail);
    }

    @GetMapping("/organizations/{organizationId}/units")
    ResponseEntity<List<OrgUnitView>> listUnits(
            @PathVariable UUID organizationId,
            @RequestParam(defaultValue = "false") boolean asTree,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listUnits(principals.current(), correlationId, organizationId, asTree), correlationId);
    }

    @PostMapping("/organizations/{organizationId}/units")
    ResponseEntity<OrgUnitView> createUnit(
            @PathVariable UUID organizationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody CreateUnitRequest request
    ) {
        OrgUnitView result = commands.createUnit(principals.current(), metadata(correlationId, idempotencyKey),
                organizationId, version(ifMatch), request.parentUnitId(), request.unitCode(), request.unitName());
        return unitResponse(result, correlationId);
    }

    @PostMapping("/organizations/{organizationId}/units/{orgUnitId}:move")
    ResponseEntity<OrgUnitView> moveUnit(
            @PathVariable UUID organizationId,
            @PathVariable UUID orgUnitId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody MoveUnitRequest request
    ) {
        OrgUnitView result = commands.moveUnit(principals.current(), metadata(correlationId, idempotencyKey),
                organizationId, orgUnitId, version(ifMatch), request.newParentUnitId());
        return unitResponse(result, correlationId);
    }

    @GetMapping("/organizations/{organizationId}/memberships")
    ResponseEntity<OrgMembershipPage> listMemberships(
            @PathVariable UUID organizationId,
            @RequestParam(required = false) UUID principalId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listMemberships(principals.current(), correlationId,
                organizationId, null, principalId), correlationId);
    }

    /**
     * 按主体聚合任职摘要；必须注册在带 path 变量的 /org-memberships/{id}:* 之前无冲突风险。
     */
    @GetMapping("/org-memberships")
    ResponseEntity<OrgMembershipSummaryPage> listMembershipSummaries(
            @RequestParam UUID principalId,
            @RequestParam(required = false) String status,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(
                queries.listMembershipSummariesForPrincipal(
                        principals.current(), correlationId, principalId, status),
                correlationId);
    }

    @PostMapping("/organizations/{organizationId}/memberships")
    ResponseEntity<OrgMembershipView> createMembership(
            @PathVariable UUID organizationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody CreateMembershipRequest request
    ) {
        OrgMembershipView result = commands.createMembership(principals.current(),
                metadata(correlationId, idempotencyKey), organizationId, request.unitId(),
                request.principalId(), request.membershipType(), request.validFrom());
        return membershipResponse(result, correlationId);
    }

    @PostMapping("/org-memberships/{membershipId}:transfer")
    ResponseEntity<OrgMembershipView> transferMembership(
            @PathVariable UUID membershipId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody TransferMembershipRequest request
    ) {
        OrgMembershipView result = commands.transferMembership(principals.current(),
                metadata(correlationId, idempotencyKey), membershipId, version(ifMatch),
                request.targetUnitId(), request.membershipType(), request.validFrom());
        return membershipResponse(result, correlationId);
    }

    @PostMapping("/org-memberships/{membershipId}:terminate")
    ResponseEntity<OrgMembershipView> terminateMembership(
            @PathVariable UUID membershipId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody TerminateMembershipRequest request
    ) {
        OrgMembershipView result = commands.terminateMembership(principals.current(),
                metadata(correlationId, idempotencyKey), membershipId, version(ifMatch),
                request.reason(), request.disablePrincipal());
        return membershipResponse(result, correlationId);
    }

    @PostMapping("/organizations/{organizationId}/directory-sync-batches")
    ResponseEntity<DirectorySyncBatchView> submitSyncBatch(
            @PathVariable UUID organizationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody SubmitSyncBatchRequest request
    ) {
        DirectorySyncBatchView result = commands.submitSyncBatch(principals.current(),
                metadata(correlationId, idempotencyKey), organizationId,
                request.sourceSystem(), request.externalBatchKey(),
                request.items().stream().map(SyncItemRequest::toInput).toList());
        return response(result, correlationId);
    }

    @GetMapping("/directory-sync-batches/{batchId}")
    ResponseEntity<DirectorySyncBatchView> getSyncBatch(
            @PathVariable UUID batchId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.getSyncBatch(principals.current(), correlationId, batchId), correlationId);
    }

    @GetMapping("/reassignment-work-items")
    ResponseEntity<ReassignmentWorkItemPage> listReassignmentWorkItems(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listOpenReassignmentWorkItems(principals.current(), correlationId), correlationId);
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

    private static ResponseEntity<OrgUnitView> unitResponse(OrgUnitView body, String correlationId) {
        return ResponseEntity.ok().eTag(Long.toString(body.version()))
                .header(CorrelationIds.HEADER_NAME, correlationId).body(body);
    }

    private static ResponseEntity<OrgMembershipView> membershipResponse(OrgMembershipView body, String correlationId) {
        return ResponseEntity.ok().eTag(Long.toString(body.version()))
                .header(CorrelationIds.HEADER_NAME, correlationId).body(body);
    }
}

record CreateOrganizationRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 32) String authorityMode,
        @Size(max = 64) String sourceSystem,
        @Size(max = 128) String sourceKey
) {}

record CreateUnitRequest(
        UUID parentUnitId,
        @NotBlank @Size(max = 64) String unitCode,
        @NotBlank @Size(max = 200) String unitName
) {}

record MoveUnitRequest(UUID newParentUnitId) {}

record CreateMembershipRequest(
        @NotNull UUID unitId,
        @NotNull UUID principalId,
        @NotBlank @Size(max = 24) String membershipType,
        @NotNull Instant validFrom
) {}

record TransferMembershipRequest(
        @NotNull UUID targetUnitId,
        @Size(max = 24) String membershipType,
        Instant validFrom
) {}

record TerminateMembershipRequest(
        @NotBlank @Size(max = 500) String reason,
        boolean disablePrincipal
) {}

record SubmitSyncBatchRequest(
        @NotBlank @Size(max = 64) String sourceSystem,
        @NotBlank @Size(max = 128) String externalBatchKey,
        @NotEmpty List<SyncItemRequest> items
) {}

record SyncItemRequest(
        @NotBlank @Size(max = 40) String operationType,
        @NotBlank @Size(max = 128) String sourceKey,
        long externalVersion,
        @Size(max = 64) String unitCode,
        @Size(max = 200) String unitName,
        @Size(max = 128) String parentSourceKey,
        UUID principalId,
        @Size(max = 24) String membershipType,
        Instant validFrom
) {
    SyncItemInput toInput() {
        return new SyncItemInput(operationType, sourceKey, externalVersion, unitCode, unitName,
                parentSourceKey, principalId, membershipType, validFrom);
    }
}
