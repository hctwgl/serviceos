package com.serviceos.fieldwork.web;

import com.serviceos.fieldwork.api.CheckInVisitCommand;
import com.serviceos.fieldwork.api.CheckOutVisitCommand;
import com.serviceos.fieldwork.api.InterruptVisitCommand;
import com.serviceos.fieldwork.api.TechnicianVisitCommandService;
import com.serviceos.fieldwork.api.VisitCommandReceipt;
import com.serviceos.fieldwork.api.VisitLocation;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Technician Portal Visit 协议适配器；不接受 tenant、network、technician 或 receivedAt。 */
@RestController
@RequestMapping("/api/v1/technician/me")
final class TechnicianVisitController {
    private final TechnicianVisitCommandService visits;
    private final CurrentPrincipalProvider principals;

    TechnicianVisitController(TechnicianVisitCommandService visits, CurrentPrincipalProvider principals) {
        this.visits = visits;
        this.principals = principals;
    }

    @PostMapping("/appointments/{appointmentId}/visits:check-in")
    ResponseEntity<VisitCommandReceipt> checkIn(
            @PathVariable UUID appointmentId,
            @RequestHeader(value = "X-Technician-Context", required = false) String technicianContext,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody CheckInRequest request
    ) {
        VisitCommandReceipt receipt = visits.checkIn(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey), technicianContext,
                new CheckInVisitCommand(appointmentId, request.capturedAt(), request.deviceCommandId(),
                        request.deviceId(), request.location().toLocation(), false));
        return ResponseEntity.created(URI.create("/api/v1/visits/" + receipt.visitId()))
                .eTag(Long.toString(receipt.aggregateVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(receipt);
    }

    @PostMapping("/visits/{visitId}:check-out")
    ResponseEntity<VisitCommandReceipt> checkOut(
            @PathVariable UUID visitId,
            @RequestHeader(value = "X-Technician-Context", required = false) String technicianContext,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody CheckOutRequest request
    ) {
        VisitCommandReceipt receipt = visits.checkOut(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey), technicianContext,
                new CheckOutVisitCommand(visitId, version(ifMatch), request.capturedAt(),
                        request.resultCode(), request.operationRefs()));
        return ok(receipt, correlationId);
    }

    @PostMapping("/visits/{visitId}:interrupt")
    ResponseEntity<VisitCommandReceipt> interrupt(
            @PathVariable UUID visitId,
            @RequestHeader(value = "X-Technician-Context", required = false) String technicianContext,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody InterruptRequest request
    ) {
        VisitCommandReceipt receipt = visits.interrupt(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey), technicianContext,
                new InterruptVisitCommand(visitId, version(ifMatch), request.capturedAt(),
                        request.exceptionCode(), request.note(), request.evidenceRefs()));
        return ok(receipt, correlationId);
    }

    private static ResponseEntity<VisitCommandReceipt> ok(VisitCommandReceipt receipt, String correlationId) {
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

    record LocationRequest(
            @DecimalMin("-90") @DecimalMax("90") double latitude,
            @DecimalMin("-180") @DecimalMax("180") double longitude,
            @DecimalMin(value = "0", inclusive = false) @DecimalMax("10000") double accuracyMeters
    ) {
        VisitLocation toLocation() {
            return new VisitLocation(latitude, longitude, accuracyMeters);
        }
    }

    record CheckInRequest(
            @NotNull Instant capturedAt,
            @NotBlank @Size(max = 160) String deviceCommandId,
            @NotBlank @Size(max = 160) String deviceId,
            @NotNull @Valid LocationRequest location
    ) {
    }

    record CheckOutRequest(
            @NotNull Instant capturedAt,
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,99}$") String resultCode,
            @NotNull @Size(min = 1, max = 50) List<@NotBlank @Size(max = 500) String> operationRefs
    ) {
    }

    record InterruptRequest(
            @NotNull Instant capturedAt,
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,99}$") String exceptionCode,
            @Size(max = 500) String note,
            @NotNull @Size(max = 50) List<@NotBlank @Size(max = 500) String> evidenceRefs
    ) {
    }
}
