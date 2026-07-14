package com.serviceos.appointment.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 爽约判定保留对象、原因和证据引用，证据内容仍由文件域治理。 */
public record MarkAppointmentNoShowCommand(
        UUID appointmentId,
        long expectedVersion,
        String noShowPartyType,
        String noShowPartyRef,
        String reasonCode,
        List<String> evidenceRefs
) {
    public MarkAppointmentNoShowCommand {
        appointmentId = Objects.requireNonNull(appointmentId, "appointmentId");
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
        noShowPartyType = text(noShowPartyType, "noShowPartyType", 80);
        noShowPartyRef = text(noShowPartyRef, "noShowPartyRef", 200);
        reasonCode = CancelAppointmentCommand.reason(reasonCode);
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        if (evidenceRefs.size() > 20 || evidenceRefs.stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 500)) {
            throw new IllegalArgumentException("evidenceRefs is invalid");
        }
    }

    private static String text(String value, String name, int max) {
        value = Objects.requireNonNull(value, name).trim();
        if (value.isEmpty() || value.length() > max) throw new IllegalArgumentException(name + " is invalid");
        return value;
    }
}
