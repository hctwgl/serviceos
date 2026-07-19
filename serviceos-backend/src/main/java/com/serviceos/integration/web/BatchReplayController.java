package com.serviceos.integration.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.integration.api.ApproveBatchReplayCommand;
import com.serviceos.integration.api.BatchReplayRequestView;
import com.serviceos.integration.api.BatchReplayService;
import com.serviceos.integration.api.CreateBatchReplayCommand;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** 批量 UNKNOWN 重放预演与审批入口。 */
@RestController
@RequestMapping("/api/v1/replay-requests")
final class BatchReplayController {
    private final BatchReplayService batches;
    private final CurrentPrincipalProvider principals;

    BatchReplayController(BatchReplayService batches, CurrentPrincipalProvider principals) {
        this.batches = batches;
        this.principals = principals;
    }

    @PostMapping
    ResponseEntity<BatchReplayRequestView> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody CreateRequest request
    ) {
        BatchReplayRequestView created = batches.create(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey),
                new CreateBatchReplayCommand(
                        request.deliveryIds(), request.mode(), request.reason(),
                        request.approvalRef(), request.maxItems()));
        return ResponseEntity.accepted().body(created);
    }

    @GetMapping("/{batchId}")
    BatchReplayRequestView get(
            @PathVariable UUID batchId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return batches.get(principals.current(), correlationId, batchId);
    }

    @PostMapping("/{batchId}:approve")
    ResponseEntity<BatchReplayRequestView> approve(
            @PathVariable UUID batchId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody ApproveRequest request
    ) {
        BatchReplayRequestView decided = batches.approve(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey),
                new ApproveBatchReplayCommand(
                        batchId, request.decision(), request.decisionNote(), request.maxItems()));
        return ResponseEntity.ok(decided);
    }

    record CreateRequest(
            @NotEmpty List<@NotNull UUID> deliveryIds,
            @NotBlank @Size(max = 24) String mode,
            @NotBlank @Size(max = 1000) String reason,
            @Size(max = 160) String approvalRef,
            Integer maxItems
    ) {
    }

    record ApproveRequest(
            @NotBlank @Size(max = 24) String decision,
            @Size(max = 1000) String decisionNote,
            Integer maxItems
    ) {
    }
}
