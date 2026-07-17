package com.serviceos.appointment.web;

import com.serviceos.appointment.api.AppointmentCommandReceipt;
import com.serviceos.appointment.api.AppointmentType;
import com.serviceos.appointment.api.AppointmentView;
import com.serviceos.appointment.api.AppointmentWindow;
import com.serviceos.appointment.api.NetworkPortalAppointmentService;
import com.serviceos.appointment.api.ProposeAppointmentCommand;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Network Portal 预约协作 HTTP 适配器。
 * <p>
 * networkId 仅来自可信头 X-Network-Context；确认方类型不得伪装 TECHNICIAN。
 */
@RestController
@RequestMapping("/api/v1/network-portal")
final class NetworkPortalAppointmentController {
    private final NetworkPortalAppointmentService portalAppointments;
    private final CurrentPrincipalProvider principals;

    NetworkPortalAppointmentController(
            NetworkPortalAppointmentService portalAppointments,
            CurrentPrincipalProvider principals
    ) {
        this.portalAppointments = portalAppointments;
        this.principals = principals;
    }

    @GetMapping("/tasks/{taskId}/appointments")
    List<AppointmentView> listByTask(
            @PathVariable UUID taskId,
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return portalAppointments.listByTask(
                principals.current(), correlationId, networkContext, taskId);
    }

    @PostMapping("/tasks/{taskId}/appointments")
    ResponseEntity<AppointmentCommandReceipt> propose(
            @PathVariable UUID taskId,
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody ProposeRequest request
    ) {
        AppointmentCommandReceipt receipt = portalAppointments.propose(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                networkContext,
                taskId,
                new ProposeAppointmentCommand(
                        taskId, request.type(), request.window().toWindow(),
                        request.addressRef(), request.addressVersion()));
        return ResponseEntity.created(URI.create("/api/v1/appointments/" + receipt.appointmentId()))
                .eTag(Long.toString(receipt.aggregateVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(receipt);
    }

    @PostMapping("/appointments/{appointmentId}:confirm")
    ResponseEntity<AppointmentCommandReceipt> confirm(
            @PathVariable UUID appointmentId,
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody ConfirmRequest request
    ) {
        AppointmentCommandReceipt receipt = portalAppointments.confirm(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                networkContext,
                appointmentId,
                version(ifMatch),
                request.confirmedPartyType(),
                request.confirmedPartyRef(),
                request.confirmationChannel());
        return receipt(receipt, correlationId);
    }

    @PostMapping("/appointments/{appointmentId}:reschedule")
    ResponseEntity<AppointmentCommandReceipt> reschedule(
            @PathVariable UUID appointmentId,
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody RescheduleRequest request
    ) {
        AppointmentCommandReceipt receipt = portalAppointments.reschedule(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                networkContext,
                appointmentId,
                version(ifMatch),
                request.newWindow().toWindow(),
                request.reasonCode(),
                request.note());
        return receipt(receipt, correlationId);
    }

    @PostMapping("/appointments/{appointmentId}:cancel")
    ResponseEntity<AppointmentCommandReceipt> cancel(
            @PathVariable UUID appointmentId,
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody CancelRequest request
    ) {
        AppointmentCommandReceipt receipt = portalAppointments.cancel(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                networkContext,
                appointmentId,
                version(ifMatch),
                request.reasonCode(),
                request.note());
        return receipt(receipt, correlationId);
    }

    private static ResponseEntity<AppointmentCommandReceipt> receipt(
            AppointmentCommandReceipt body, String correlationId
    ) {
        return ResponseEntity.ok()
                .eTag(Long.toString(body.aggregateVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(body);
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

    record WindowRequest(
            @NotNull Instant start,
            @NotNull Instant end,
            @NotBlank @Size(max = 80) String timezone,
            @Min(1) @Max(1440) int estimatedDurationMinutes
    ) {
        AppointmentWindow toWindow() {
            return new AppointmentWindow(start, end, timezone, estimatedDurationMinutes);
        }
    }

    record ProposeRequest(
            @NotNull AppointmentType type,
            @NotNull @Valid WindowRequest window,
            @NotBlank @Size(max = 200) String addressRef,
            @NotBlank @Size(max = 100) String addressVersion
    ) {
    }

    record ConfirmRequest(
            @NotBlank @Size(max = 80) String confirmedPartyType,
            @NotBlank @Size(max = 200) String confirmedPartyRef,
            @NotBlank @Size(max = 80) String confirmationChannel
    ) {
    }

    record RescheduleRequest(
            @NotNull @Valid WindowRequest newWindow,
            @NotBlank @Size(max = 100) String reasonCode,
            @Size(max = 500) String note
    ) {
    }

    record CancelRequest(
            @NotBlank @Size(max = 100) String reasonCode,
            @Size(max = 500) String note
    ) {
    }
}
