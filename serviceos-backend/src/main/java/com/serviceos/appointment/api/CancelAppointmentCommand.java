package com.serviceos.appointment.api;

import java.util.Objects;
import java.util.UUID;

/** 取消预约命令必须携带乐观锁版本和受控原因。 */
public record CancelAppointmentCommand(UUID appointmentId, long expectedVersion, String reasonCode, String note) {
    public CancelAppointmentCommand {
        appointmentId = Objects.requireNonNull(appointmentId, "appointmentId");
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
        reasonCode = reason(reasonCode);
        if (note != null && note.length() > 500) throw new IllegalArgumentException("note is too long");
    }

    static String reason(String value) {
        value = Objects.requireNonNull(value, "reasonCode").trim();
        if (!value.matches("[A-Z][A-Z0-9_]{1,99}")) throw new IllegalArgumentException("reasonCode is invalid");
        return value;
    }
}
