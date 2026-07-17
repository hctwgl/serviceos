package com.serviceos.appointment.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.List;
import java.util.UUID;

/** 预约聚合的查询与命令公开边界。 */
public interface AppointmentService {
    List<AppointmentView> listByTask(CurrentPrincipal principal, String correlationId, UUID taskId);

    AppointmentView get(CurrentPrincipal principal, String correlationId, UUID appointmentId);

    List<ContactAttemptView> listContactAttempts(CurrentPrincipal principal, String correlationId, UUID taskId);

    ContactAttemptView getContactAttempt(CurrentPrincipal principal, String correlationId, UUID contactAttemptId);

    ContactAttemptView recordContactAttempt(
            CurrentPrincipal principal, CommandMetadata metadata, RecordContactAttemptCommand command);

    AppointmentCommandReceipt propose(
            CurrentPrincipal principal, CommandMetadata metadata, ProposeAppointmentCommand command);

    AppointmentCommandReceipt confirm(
            CurrentPrincipal principal, CommandMetadata metadata, ConfirmAppointmentCommand command);

    AppointmentCommandReceipt reschedule(
            CurrentPrincipal principal, CommandMetadata metadata, RescheduleAppointmentCommand command);

    AppointmentCommandReceipt cancel(
            CurrentPrincipal principal, CommandMetadata metadata, CancelAppointmentCommand command);

    AppointmentCommandReceipt markNoShow(
            CurrentPrincipal principal, CommandMetadata metadata, MarkAppointmentNoShowCommand command);
}
