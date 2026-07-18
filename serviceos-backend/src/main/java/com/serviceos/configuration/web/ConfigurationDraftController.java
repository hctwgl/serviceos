package com.serviceos.configuration.web;

import com.serviceos.configuration.api.ApproveConfigurationDraftCommand;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationDependencyAnalysisService;
import com.serviceos.configuration.api.ConfigurationDependencyReport;
import com.serviceos.configuration.api.ConfigurationDraftDiffView;
import com.serviceos.configuration.api.ConfigurationDraftService;
import com.serviceos.configuration.api.ConfigurationDraftView;
import com.serviceos.configuration.api.CreateConfigurationDraftCommand;
import com.serviceos.configuration.api.UpdateConfigurationDraftCommand;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 配置设计器协议适配：草稿保存、校验、Diff、审批与发布。 */
@RestController
@RequestMapping("/api/v1/configuration/drafts")
final class ConfigurationDraftController {
    private final ConfigurationDraftService drafts;
    private final ConfigurationDependencyAnalysisService dependencies;
    private final CurrentPrincipalProvider principals;

    ConfigurationDraftController(
            ConfigurationDraftService drafts,
            ConfigurationDependencyAnalysisService dependencies,
            CurrentPrincipalProvider principals
    ) {
        this.drafts = drafts;
        this.dependencies = dependencies;
        this.principals = principals;
    }

    @PostMapping
    ResponseEntity<DraftResponse> create(
            @RequestBody CreateDraftRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ConfigurationDraftView view = drafts.create(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey == null ? correlationId : idempotencyKey),
                new CreateConfigurationDraftCommand(
                        ConfigurationAssetType.valueOf(request.assetType()),
                        request.assetKey(),
                        request.intendedSemanticVersion(),
                        request.schemaVersion(),
                        request.definitionJson(),
                        request.baseVersionId()));
        return ok(view, correlationId);
    }

    @PutMapping("/{draftId}")
    ResponseEntity<DraftResponse> update(
            @PathVariable UUID draftId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody UpdateDraftRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ConfigurationDraftView view = drafts.update(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey == null ? correlationId : idempotencyKey),
                new UpdateConfigurationDraftCommand(draftId, version(ifMatch), request.definitionJson()));
        return ok(view, correlationId);
    }

    @GetMapping("/{draftId}")
    ResponseEntity<DraftResponse> get(
            @PathVariable UUID draftId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return ok(drafts.get(principals.current(), correlationId, draftId), correlationId);
    }

    @GetMapping
    ResponseEntity<List<DraftResponse>> list(
            @RequestParam(defaultValue = "WORKFLOW") String assetType,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        List<DraftResponse> body = drafts.list(
                        principals.current(), correlationId, ConfigurationAssetType.valueOf(assetType))
                .stream().map(ConfigurationDraftController::toResponse).toList();
        return ResponseEntity.ok().header(CorrelationIds.HEADER_NAME, correlationId).body(body);
    }

    @PostMapping("/{draftId}:validate")
    ResponseEntity<DraftResponse> validate(
            @PathVariable UUID draftId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ConfigurationDraftView view = drafts.validate(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey == null ? correlationId : idempotencyKey),
                draftId);
        return ok(view, correlationId);
    }

    @GetMapping("/{draftId}:diff")
    ResponseEntity<DiffResponse> diff(
            @PathVariable UUID draftId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ConfigurationDraftDiffView view = drafts.diff(principals.current(), correlationId, draftId);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(new DiffResponse(
                        view.draftId(), view.baseVersionId(), view.baseLabel(),
                        view.draftLabel(), view.unifiedDiff(), view.identical()));
    }

    @PostMapping("/{draftId}:approve")
    ResponseEntity<DraftResponse> approve(
            @PathVariable UUID draftId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody ApproveDraftRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ConfigurationDraftView view = drafts.approve(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey == null ? correlationId : idempotencyKey),
                new ApproveConfigurationDraftCommand(draftId, version(ifMatch), request.approvalRef()));
        return ok(view, correlationId);
    }

    @GetMapping("/{draftId}:dependencies")
    ResponseEntity<DependencyReportHttpModels.DependencyReportResponse> dependencies(
            @PathVariable UUID draftId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ConfigurationDependencyReport report = dependencies.analyzeDraft(
                principals.current(), correlationId, draftId);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(toDependencyResponse(report));
    }

    @PostMapping("/{draftId}:publish")
    ResponseEntity<DraftResponse> publish(
            @PathVariable UUID draftId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ConfigurationDraftView view = drafts.publish(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey == null ? correlationId : idempotencyKey),
                draftId);
        return ok(view, correlationId);
    }

    private static ResponseEntity<DraftResponse> ok(ConfigurationDraftView view, String correlationId) {
        return ResponseEntity.ok()
                .eTag(Long.toString(view.aggregateVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(toResponse(view));
    }

    private static DraftResponse toResponse(ConfigurationDraftView view) {
        return new DraftResponse(
                view.draftId(), view.assetType().name(), view.assetKey(),
                view.intendedSemanticVersion(), view.schemaVersion(), view.definitionJson(),
                view.contentDigest(), view.status(), view.baseVersionId(), view.publishedVersionId(),
                view.validationErrors(), view.approvalRef(), view.approvedBy(), view.approvedAt(),
                view.aggregateVersion(), view.createdBy(), view.updatedBy(),
                view.createdAt(), view.updatedAt());
    }

    private static long version(String ifMatch) {
        try {
            return Long.parseLong(ifMatch.replace("\"", "").trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("If-Match aggregate version is invalid");
        }
    }

    private static DependencyReportHttpModels.DependencyReportResponse toDependencyResponse(
            ConfigurationDependencyReport report
    ) {
        return new DependencyReportHttpModels.DependencyReportResponse(
                report.assetType().name(),
                report.assetKey(),
                report.draftId(),
                report.bundleId(),
                report.complete(),
                report.dependencies().stream()
                        .map(item -> new DependencyReportHttpModels.DependencyItemResponse(
                                item.refField(), item.refValue(), item.sourceNodeId(),
                                item.expectedAssetType().name(), item.status().name(),
                                item.satisfiedVersionId(), item.detail()))
                        .toList());
    }

    record CreateDraftRequest(
            String assetType,
            String assetKey,
            String intendedSemanticVersion,
            String schemaVersion,
            String definitionJson,
            UUID baseVersionId
    ) {
    }

    record UpdateDraftRequest(String definitionJson) {
    }

    record ApproveDraftRequest(String approvalRef) {
    }

    record DiffResponse(
            UUID draftId,
            UUID baseVersionId,
            String baseLabel,
            String draftLabel,
            String unifiedDiff,
            boolean identical
    ) {
    }

    record DraftResponse(
            UUID draftId,
            String assetType,
            String assetKey,
            String intendedSemanticVersion,
            String schemaVersion,
            String definitionJson,
            String contentDigest,
            String status,
            UUID baseVersionId,
            UUID publishedVersionId,
            List<String> validationErrors,
            String approvalRef,
            String approvedBy,
            Instant approvedAt,
            long aggregateVersion,
            String createdBy,
            String updatedBy,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
