package com.serviceos.appointment.api;

import java.util.Objects;
import java.util.UUID;

/** 确认当前预约修订；expectedVersion 来自双引号 ETag。 */
public record ConfirmAppointmentCommand(
        UUID appointmentId,
        long expectedVersion,
        String confirmedPartyType,
        String confirmedPartyRef,
        String confirmationChannel
) {
    public ConfirmAppointmentCommand {
        appointmentId = Objects.requireNonNull(appointmentId, "appointmentId");
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
        confirmedPartyType = code(confirmedPartyType, "confirmedPartyType");
        confirmedPartyRef = text(confirmedPartyRef, "confirmedPartyRef", 200);
        confirmationChannel = code(confirmationChannel, "confirmationChannel");
    }

    private static String code(String value, String name) {
        String normalized = text(value, name, 80).toUpperCase();
        if (!normalized.matches("[A-Z][A-Z0-9_]{1,79}")) {
            throw new IllegalArgumentException(name + " must be a stable uppercase code");
        }
        return normalized;
    }

    private static String text(String value, String name, int max) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException(name + " must contain at most " + max + " characters");
        }
        return normalized;
    }
}
