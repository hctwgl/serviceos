package com.serviceos.dispatch.web;

import com.serviceos.dispatch.api.CapacityAuthorityService;
import com.serviceos.dispatch.api.CapacityCounterReceipt;
import com.serviceos.dispatch.api.ConfigureCapacityCommand;
import com.serviceos.dispatch.api.ResponsibilityLevel;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 派单容量配置 HTTP 适配器；占用量仍只能由预留事务修改。 */
@RestController
@RequestMapping("/api/v1/dispatch/capacities")
final class CapacityController {
    private final CapacityAuthorityService capacities;
    private final CurrentPrincipalProvider principals;

    CapacityController(CapacityAuthorityService capacities, CurrentPrincipalProvider principals) {
        this.capacities = capacities;
        this.principals = principals;
    }

    @PostMapping
    ResponseEntity<CapacityCounterReceipt> configure(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody ConfigureCapacityRequest request
    ) {
        CapacityCounterReceipt result = capacities.configure(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                new ConfigureCapacityCommand(
                        request.responsibilityLevel(), request.assigneeId(), request.businessType(),
                        request.maxUnits(), request.expectedVersion()));
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .eTag(Long.toString(result.version()))
                .body(result);
    }

    record ConfigureCapacityRequest(
            @NotNull ResponsibilityLevel responsibilityLevel,
            @NotBlank @Size(max = 128) String assigneeId,
            @NotBlank @Size(max = 100) String businessType,
            @Min(1) int maxUnits,
            @Min(0) long expectedVersion
    ) {}
}
