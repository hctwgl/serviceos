package com.serviceos.configuration.web;

import com.serviceos.configuration.api.CreateProjectFulfillmentProfileCommand;
import com.serviceos.configuration.api.ProjectFulfillmentCompareImpact;
import com.serviceos.configuration.api.ProjectFulfillmentDraftView;
import com.serviceos.configuration.api.ProjectFulfillmentManifestView;
import com.serviceos.configuration.api.ProjectFulfillmentProfileDetail;
import com.serviceos.configuration.api.ProjectFulfillmentProfileService;
import com.serviceos.configuration.api.ProjectFulfillmentProfileSummary;
import com.serviceos.configuration.api.ProjectFulfillmentRevisionView;
import com.serviceos.configuration.api.ProjectFulfillmentValidationIssue;
import com.serviceos.configuration.api.UpdateProjectFulfillmentDraftCommand;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 项目工单类型履约配置 HTTP 适配。 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/fulfillment-profiles")
final class ProjectFulfillmentProfileController {
    private final ProjectFulfillmentProfileService profiles;
    private final CurrentPrincipalProvider principals;

    ProjectFulfillmentProfileController(
            ProjectFulfillmentProfileService profiles,
            CurrentPrincipalProvider principals
    ) {
        this.profiles = profiles;
        this.principals = principals;
    }

    @GetMapping
    ResponseEntity<List<ProjectFulfillmentProfileSummary>> list(
            @PathVariable UUID projectId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(profiles.list(principals.current(), correlationId, projectId));
    }

    @PostMapping
    ResponseEntity<ProjectFulfillmentProfileDetail> create(
            @PathVariable UUID projectId,
            @RequestBody CreateProfileRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ProjectFulfillmentProfileDetail detail = profiles.create(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                new CreateProjectFulfillmentProfileCommand(
                        projectId,
                        request.serviceProductCode(),
                        request.profileName(),
                        request.description(),
                        request.templateCode(),
                        request.copyFromProfileId()));
        return ResponseEntity
                .created(URI.create("/api/v1/projects/" + projectId
                        + "/fulfillment-profiles/" + detail.profileId()))
                .eTag(Long.toString(detail.aggregateVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(detail);
    }

    @GetMapping("/{profileId}")
    ResponseEntity<ProjectFulfillmentProfileDetail> get(
            @PathVariable UUID projectId,
            @PathVariable UUID profileId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ProjectFulfillmentProfileDetail detail = profiles.get(
                principals.current(), correlationId, projectId, profileId);
        return ResponseEntity.ok()
                .eTag(Long.toString(detail.aggregateVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(detail);
    }

    @GetMapping("/{profileId}/draft")
    ResponseEntity<ProjectFulfillmentDraftView> getDraft(
            @PathVariable UUID projectId,
            @PathVariable UUID profileId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ProjectFulfillmentDraftView draft = profiles.getDraft(
                principals.current(), correlationId, projectId, profileId);
        return ResponseEntity.ok()
                .eTag(Long.toString(draft.aggregateVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(draft);
    }

    @PutMapping("/{profileId}/draft")
    ResponseEntity<ProjectFulfillmentDraftView> updateDraft(
            @PathVariable UUID projectId,
            @PathVariable UUID profileId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody UpdateDraftRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ProjectFulfillmentDraftView draft = profiles.updateDraft(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey == null ? correlationId : idempotencyKey),
                new UpdateProjectFulfillmentDraftCommand(
                        profileId,
                        version(ifMatch),
                        request.profileName(),
                        request.description(),
                        request.documentJson(),
                        request.workflowAssetVersionId(),
                        request.sourceBundleId()));
        return ResponseEntity.ok()
                .eTag(Long.toString(draft.aggregateVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(draft);
    }

    @PostMapping("/{profileId}:validate")
    ResponseEntity<List<ProjectFulfillmentValidationIssue>> validate(
            @PathVariable UUID projectId,
            @PathVariable UUID profileId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(profiles.validate(
                        principals.current(),
                        new CommandMetadata(correlationId, idempotencyKey == null ? correlationId : idempotencyKey),
                        projectId, profileId));
    }

    @PostMapping("/{profileId}:compile-preview")
    ResponseEntity<ProjectFulfillmentManifestView> compilePreview(
            @PathVariable UUID projectId,
            @PathVariable UUID profileId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(profiles.compilePreview(
                        principals.current(),
                        new CommandMetadata(correlationId, idempotencyKey == null ? correlationId : idempotencyKey),
                        projectId, profileId));
    }

    @GetMapping("/{profileId}/compare-impact")
    ResponseEntity<ProjectFulfillmentCompareImpact> compareImpact(
            @PathVariable UUID projectId,
            @PathVariable UUID profileId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(profiles.compareImpact(
                        principals.current(), correlationId, projectId, profileId));
    }

    @PostMapping("/{profileId}:publish")
    ResponseEntity<ProjectFulfillmentRevisionView> publish(
            @PathVariable UUID projectId,
            @PathVariable UUID profileId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody PublishRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ProjectFulfillmentRevisionView revision = profiles.publish(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                projectId,
                profileId,
                version(ifMatch),
                request.effectiveFrom(),
                request.publishNote());
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(revision);
    }

    @GetMapping("/{profileId}/revisions")
    ResponseEntity<List<ProjectFulfillmentRevisionView>> listRevisions(
            @PathVariable UUID projectId,
            @PathVariable UUID profileId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(profiles.listRevisions(principals.current(), correlationId, projectId, profileId));
    }

    @GetMapping("/{profileId}/revisions/{revisionId}")
    ResponseEntity<ProjectFulfillmentRevisionView> getRevision(
            @PathVariable UUID projectId,
            @PathVariable UUID profileId,
            @PathVariable UUID revisionId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(profiles.getRevision(
                        principals.current(), correlationId, projectId, profileId, revisionId));
    }

    @PostMapping("/{profileId}:suspend")
    ResponseEntity<ProjectFulfillmentProfileDetail> suspend(
            @PathVariable UUID projectId,
            @PathVariable UUID profileId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ProjectFulfillmentProfileDetail detail = profiles.suspend(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                projectId, profileId, version(ifMatch));
        return ResponseEntity.ok()
                .eTag(Long.toString(detail.aggregateVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(detail);
    }

    @PostMapping("/{profileId}:resume")
    ResponseEntity<ProjectFulfillmentProfileDetail> resume(
            @PathVariable UUID projectId,
            @PathVariable UUID profileId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ProjectFulfillmentProfileDetail detail = profiles.resume(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                projectId, profileId, version(ifMatch));
        return ResponseEntity.ok()
                .eTag(Long.toString(detail.aggregateVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(detail);
    }

    private static long version(String ifMatch) {
        try {
            return Long.parseLong(ifMatch.replace("\"", "").trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("If-Match aggregate version is invalid");
        }
    }

    record CreateProfileRequest(
            String serviceProductCode,
            String profileName,
            String description,
            String templateCode,
            UUID copyFromProfileId
    ) {
    }

    record UpdateDraftRequest(
            String profileName,
            String description,
            String documentJson,
            UUID workflowAssetVersionId,
            UUID sourceBundleId
    ) {
    }

    record PublishRequest(Instant effectiveFrom, String publishNote) {
    }
}
