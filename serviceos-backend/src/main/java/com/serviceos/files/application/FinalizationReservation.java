package com.serviceos.files.application;

import java.util.UUID;

public record FinalizationReservation(
        UploadSessionRecord session,
        UUID token,
        boolean replay,
        boolean expired
) {
    public static FinalizationReservation reserved(UploadSessionRecord session, UUID token) {
        return new FinalizationReservation(session, token, false, false);
    }

    public static FinalizationReservation replay(UploadSessionRecord session) {
        return new FinalizationReservation(session, null, true, false);
    }

    public static FinalizationReservation expired(UploadSessionRecord session) {
        return new FinalizationReservation(session, null, false, true);
    }
}
