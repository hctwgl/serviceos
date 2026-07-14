package com.serviceos.appointment.api;

import java.time.Instant;
import java.util.UUID;

/** 命令首次提交后的冻结权威结果。 */
public record AppointmentCommandReceipt(
        UUID appointmentId,
        UUID revisionId,
        String status,
        int revisionNo,
        long aggregateVersion,
        Instant occurredAt
) {
}
