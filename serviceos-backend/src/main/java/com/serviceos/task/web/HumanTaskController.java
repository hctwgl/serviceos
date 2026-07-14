package com.serviceos.task.web;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import com.serviceos.task.api.ClaimHumanTaskCommand;
import com.serviceos.task.api.CompleteHumanTaskCommand;
import com.serviceos.task.api.HumanTaskCommandReceipt;
import com.serviceos.task.api.HumanTaskCommandService;
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
                new ClaimHumanTaskCommand(taskId, version(ifMatch)));
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
                new StartHumanTaskCommand(taskId, version(ifMatch)));
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
                        taskId, version(ifMatch), request.resultRef(), request.resultDigest()));
        return response(receipt, correlationId);
    }

    private static ResponseEntity<HumanTaskCommandReceipt> response(
            HumanTaskCommandReceipt receipt, String correlationId) {
        return ResponseEntity.ok()
                .eTag(Long.toString(receipt.version()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(receipt);
    }

    private static long version(String ifMatch) {
        if (ifMatch == null || !ifMatch.matches("\"[1-9][0-9]*\"")) {
            throw new IllegalArgumentException("If-Match must contain one quoted positive aggregate version");
        }
        try {
            return Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("If-Match aggregate version is too large", exception);
        }
    }
}
