package com.serviceos.operations.web;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.operations.api.AcknowledgeOperationalExceptionCommand;
import com.serviceos.operations.api.OperationalExceptionAcknowledgement;
import com.serviceos.operations.api.OperationalExceptionItem;
import com.serviceos.operations.api.OperationalExceptionPage;
import com.serviceos.operations.api.OperationalExceptionQuery;
import com.serviceos.operations.api.OperationalExceptionWorkbenchService;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
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

import java.util.UUID;

/** 运营异常工作台 HTTP 适配器；tenant 与 actor 始终来自已验证 JWT 主体。 */
@RestController
@RequestMapping("/api/v1/operational-exceptions")
final class OperationalExceptionController {
    private final OperationalExceptionWorkbenchService workbench;
    private final CurrentPrincipalProvider principals;

    OperationalExceptionController(
            OperationalExceptionWorkbenchService workbench, CurrentPrincipalProvider principals
    ) {
        this.workbench = workbench;
        this.principals = principals;
    }

    @GetMapping
    OperationalExceptionPage list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) UUID workOrderId,
            @RequestParam(required = false) UUID taskId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return workbench.list(principals.current(), correlationId,
                new OperationalExceptionQuery(
                        status, category, severity, workOrderId, taskId, cursor, limit));
    }

    @GetMapping("/{exceptionId}")
    OperationalExceptionItem get(
            @PathVariable UUID exceptionId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return workbench.get(principals.current(), correlationId, exceptionId);
    }

    @PostMapping("/{exceptionId}:acknowledge")
    ResponseEntity<OperationalExceptionAcknowledgement> acknowledge(
            @PathVariable UUID exceptionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody AcknowledgeRequest request
    ) {
        CurrentPrincipal principal = principals.current();
        OperationalExceptionAcknowledgement receipt = workbench.acknowledge(
                principal, new CommandMetadata(correlationId, idempotencyKey),
                new AcknowledgeOperationalExceptionCommand(exceptionId, version(ifMatch), request.note()));
        return ResponseEntity.ok().eTag(Long.toString(receipt.aggregateVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId).body(receipt);
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

    record AcknowledgeRequest(@Size(max = 500) String note) {}
}
