package com.serviceos.appointment.api;

import java.util.Objects;
import java.util.UUID;

/** 首期所有改约都要求重新确认，服务端不会接受客户端自报“预授权确认”。 */
public record RescheduleAppointmentCommand(
        UUID appointmentId,
        long expectedVersion,
        AppointmentWindow newWindow,
        String reasonCode,
        String note
) {
    public RescheduleAppointmentCommand {
        appointmentId = Objects.requireNonNull(appointmentId, "appointmentId");
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
        newWindow = Objects.requireNonNull(newWindow, "newWindow");
        reasonCode = Objects.requireNonNull(reasonCode, "reasonCode").trim().toUpperCase();
        if (!reasonCode.matches("[A-Z][A-Z0-9_]{1,99}")) {
            throw new IllegalArgumentException("reasonCode must be a stable uppercase code");
        }
        if (note != null) {
            note = note.trim();
            if (note.length() > 500) throw new IllegalArgumentException("note is too long");
        }
    }
}
