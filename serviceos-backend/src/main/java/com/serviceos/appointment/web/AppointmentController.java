package com.serviceos.appointment.web;

import com.serviceos.appointment.api.AppointmentCommandReceipt;
import com.serviceos.appointment.api.AppointmentService;
import com.serviceos.appointment.api.AppointmentType;
import com.serviceos.appointment.api.AppointmentView;
import com.serviceos.appointment.api.AppointmentWindow;
import com.serviceos.appointment.api.CancelAppointmentCommand;
import com.serviceos.appointment.api.ConfirmAppointmentCommand;
import com.serviceos.appointment.api.ContactAttemptView;
import com.serviceos.appointment.api.ContactResultCode;
import com.serviceos.appointment.api.MarkAppointmentNoShowCommand;
import com.serviceos.appointment.api.ProposeAppointmentCommand;
import com.serviceos.appointment.api.RescheduleAppointmentCommand;
import com.serviceos.appointment.api.RecordContactAttemptCommand;
import com.serviceos.identity.api.CurrentPrincipal;
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

/** 预约 HTTP 适配器；租户和操作者始终来自受信 JWT 主体。 */
@RestController
@RequestMapping("/api/v1")
final class AppointmentController {
    private final AppointmentService appointments;
    private final CurrentPrincipalProvider principals;

    AppointmentController(AppointmentService appointments, CurrentPrincipalProvider principals) {
        this.appointments = appointments;
        this.principals = principals;
    }

    @GetMapping("/tasks/{taskId}/appointments")
    List<AppointmentView> listByTask(
            @PathVariable UUID taskId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return appointments.listByTask(principals.current(), correlationId, taskId);
    }

    @GetMapping("/appointments/{appointmentId}")
    ResponseEntity<AppointmentView> get(
            @PathVariable UUID appointmentId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        AppointmentView appointment = appointments.get(principals.current(), correlationId, appointmentId);
        return ResponseEntity.ok().eTag(Long.toString(appointment.aggregateVersion())).body(appointment);
    }

    @GetMapping("/tasks/{taskId}/contact-attempts")
    List<ContactAttemptView> listContactAttempts(
            @PathVariable UUID taskId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return appointments.listContactAttempts(principals.current(), correlationId, taskId);
    }

    @GetMapping("/contact-attempts/{contactAttemptId}")
    ResponseEntity<ContactAttemptView> getContactAttempt(
            @PathVariable UUID contactAttemptId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        // 不可变联系事实无 aggregateVersion，故不返回 ETag。
        ContactAttemptView attempt = appointments.getContactAttempt(
                principals.current(), correlationId, contactAttemptId);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(attempt);
    }

    @PostMapping("/tasks/{taskId}/contact-attempts")
    ResponseEntity<ContactAttemptView> recordContactAttempt(
            @PathVariable UUID taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody ContactAttemptRequest request
    ) {
        ContactAttemptView attempt = appointments.recordContactAttempt(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey),
                new RecordContactAttemptCommand(
                        taskId, request.channel(), request.contactedPartyRef(), request.startedAt(),
                        request.endedAt(), request.resultCode(), request.note(), request.nextContactAt(),
                        request.recordingRef()));
        return ResponseEntity.created(URI.create("/api/v1/contact-attempts/" + attempt.contactAttemptId()))
                .header(CorrelationIds.HEADER_NAME, correlationId).body(attempt);
    }

    @PostMapping("/tasks/{taskId}/appointments")
    ResponseEntity<AppointmentCommandReceipt> propose(
            @PathVariable UUID taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody ProposeRequest request
    ) {
        CurrentPrincipal principal = principals.current();
        AppointmentCommandReceipt receipt = appointments.propose(
                principal, new CommandMetadata(correlationId, idempotencyKey),
                new ProposeAppointmentCommand(
                        taskId, request.type(), request.window().toWindow(),
                        request.addressRef(), request.addressVersion()));
        return ResponseEntity.created(URI.create("/api/v1/appointments/" + receipt.appointmentId()))
                .eTag(Long.toString(receipt.aggregateVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId).body(receipt);
    }

    @PostMapping("/appointments/{appointmentId}:confirm")
    ResponseEntity<AppointmentCommandReceipt> confirm(
            @PathVariable UUID appointmentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody ConfirmRequest request
    ) {
        AppointmentCommandReceipt receipt = appointments.confirm(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey),
                new ConfirmAppointmentCommand(
                        appointmentId, version(ifMatch), request.confirmedPartyType(),
                        request.confirmedPartyRef(), request.confirmationChannel()));
        return response(receipt, correlationId);
    }

    @PostMapping("/appointments/{appointmentId}:reschedule")
    ResponseEntity<AppointmentCommandReceipt> reschedule(
            @PathVariable UUID appointmentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody RescheduleRequest request
    ) {
        AppointmentCommandReceipt receipt = appointments.reschedule(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey),
                new RescheduleAppointmentCommand(
                        appointmentId, version(ifMatch), request.newWindow().toWindow(),
                        request.reasonCode(), request.note()));
        return response(receipt, correlationId);
    }

    @PostMapping("/appointments/{appointmentId}:cancel")
    ResponseEntity<AppointmentCommandReceipt> cancel(
            @PathVariable UUID appointmentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody CancelRequest request
    ) {
        AppointmentCommandReceipt receipt = appointments.cancel(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey),
                new CancelAppointmentCommand(appointmentId, version(ifMatch), request.reasonCode(), request.note()));
        return response(receipt, correlationId);
    }

    @PostMapping("/appointments/{appointmentId}:mark-no-show")
    ResponseEntity<AppointmentCommandReceipt> markNoShow(
            @PathVariable UUID appointmentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody MarkNoShowRequest request
    ) {
        AppointmentCommandReceipt receipt = appointments.markNoShow(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey),
                new MarkAppointmentNoShowCommand(
                        appointmentId, version(ifMatch), request.noShowPartyType(),
                        request.noShowPartyRef(), request.reasonCode(), request.evidenceRefs()));
        return response(receipt, correlationId);
    }

    private static ResponseEntity<AppointmentCommandReceipt> response(
            AppointmentCommandReceipt receipt, String correlationId
    ) {
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

    record ContactAttemptRequest(
            @NotBlank @Size(max = 80) String channel,
            @NotBlank @Size(max = 200) String contactedPartyRef,
            @NotNull Instant startedAt,
            @NotNull Instant endedAt,
            @NotNull ContactResultCode resultCode,
            @Size(max = 500) String note,
            Instant nextContactAt,
            @Size(max = 500) String recordingRef
    ) {
    }

    record CancelRequest(
            @NotBlank @Size(max = 100) String reasonCode,
            @Size(max = 500) String note
    ) {
    }

    record MarkNoShowRequest(
            @NotBlank @Size(max = 80) String noShowPartyType,
            @NotBlank @Size(max = 200) String noShowPartyRef,
            @NotBlank @Size(max = 100) String reasonCode,
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 500) String> evidenceRefs
    ) {
    }
}
