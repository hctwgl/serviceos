package com.serviceos.appointment.api;

import java.time.Instant;
import java.util.UUID;

/** 不可变预约修订；确认通过新增修订表达，不回写原提议。 */
public record AppointmentRevisionView(
        UUID revisionId,
        int revisionNo,
        UUID previousRevisionId,
        String revisionKind,
        AppointmentWindow window,
        String addressRef,
        String addressVersion,
        String confirmedPartyType,
        String confirmedPartyRef,
        String confirmationChannel,
        Instant confirmedAt,
        String reasonCode,
        String note,
        String noShowPartyType,
        String noShowPartyRef,
        java.util.List<String> noShowEvidenceRefs,
        String createdBy,
        Instant createdAt
) {
}
