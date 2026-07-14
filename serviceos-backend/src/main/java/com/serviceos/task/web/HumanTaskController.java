package com.serviceos.task.web;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import com.serviceos.task.api.ClaimHumanTaskCommand;
import com.serviceos.task.api.CompleteHumanTaskCommand;
import com.serviceos.task.api.HumanTaskCommandReceipt;
import com.serviceos.task.api.HumanTaskCommandService;
import com.serviceos.task.api.ReleaseHumanTaskCommand;
import com.serviceos.task.api.StartHumanTaskCommand;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 人工任务动作 HTTP 适配器。
 *
 * <p>主体来自 JWT 映射，聚合版本来自 If-Match，幂等键来自标准命令头；请求正文只承载业务结果。</p>
 */
@RestController
@RequestMapping("/api/v1/tasks")
final class HumanTaskController {
    private final HumanTaskCommandService commands;
    private final CurrentPrincipalProvider principals;

    HumanTaskController(HumanTaskCommandService commands, CurrentPrincipalProvider principals) {
        this.commands = commands;
        this.principals = principals;
    }

    @PostMapping("/{taskId}:claim")
    ResponseEntity<HumanTaskCommandReceipt> claim(
            @PathVariable UUID taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        CurrentPrincipal principal = principals.current();
        HumanTaskCommandReceipt receipt = commands.claim(
                principal, new CommandMetadata(correlationId, idempotencyKey),
                new ClaimHumanTaskCommand(taskId, TaskHttpPreconditions.version(ifMatch)));
        return response(receipt, correlationId);
    }

    @PostMapping("/{taskId}:start")
    ResponseEntity<HumanTaskCommandReceipt> start(
            @PathVariable UUID taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        CurrentPrincipal principal = principals.current();
        HumanTaskCommandReceipt receipt = commands.start(
                principal, new CommandMetadata(correlationId, idempotencyKey),
                new StartHumanTaskCommand(taskId, TaskHttpPreconditions.version(ifMatch)));
        return response(receipt, correlationId);
    }

    @PostMapping("/{taskId}:complete")
    ResponseEntity<HumanTaskCommandReceipt> complete(
            @PathVariable UUID taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody CompleteHumanTaskRequest request
    ) {
        CurrentPrincipal principal = principals.current();
        HumanTaskCommandReceipt receipt = commands.complete(
                principal, new CommandMetadata(correlationId, idempotencyKey),
                new CompleteHumanTaskCommand(
                        taskId, TaskHttpPreconditions.version(ifMatch),
                        request.resultRef(), request.resultDigest(),
                        request.inputVersionRefs() == null
                                ? java.util.List.of()
                                : request.inputVersionRefs().stream()
                                .map(CompleteHumanTaskRequest.InputVersionRefRequest::toApi)
                                .toList()));
        return response(receipt, correlationId);
    }

    @PostMapping("/{taskId}:release")
    ResponseEntity<HumanTaskCommandReceipt> release(
            @PathVariable UUID taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody ReleaseHumanTaskRequest request
    ) {
        CurrentPrincipal principal = principals.current();
        HumanTaskCommandReceipt receipt = commands.release(
                principal, new CommandMetadata(correlationId, idempotencyKey),
                new ReleaseHumanTaskCommand(
                        taskId, TaskHttpPreconditions.version(ifMatch), request.reasonCode()));
        return response(receipt, correlationId);
    }

    private static ResponseEntity<HumanTaskCommandReceipt> response(
            HumanTaskCommandReceipt receipt, String correlationId) {
        return ResponseEntity.ok()
                .eTag(Long.toString(receipt.version()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(receipt);
    }

}
