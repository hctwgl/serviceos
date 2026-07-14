package com.serviceos.fieldwork.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 无法施工或安全中断必须使用标准异常码，并可引用受治理证据。 */
public record InterruptVisitCommand(
        UUID visitId,
        long expectedVersion,
        Instant capturedAt,
        String exceptionCode,
        String note,
        List<String> evidenceRefs
) {
    public InterruptVisitCommand {
        visitId = Objects.requireNonNull(visitId, "visitId");
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
        capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
        exceptionCode = CheckOutVisitCommand.reason(exceptionCode, "exceptionCode");
        if (note != null && note.length() > 500) throw new IllegalArgumentException("note is too long");
        evidenceRefs = CheckOutVisitCommand.references(evidenceRefs, "evidenceRefs", true);
    }
}
