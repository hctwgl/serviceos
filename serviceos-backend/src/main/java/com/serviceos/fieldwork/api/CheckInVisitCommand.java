package com.serviceos.fieldwork.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 到场命令显式携带设备命令身份，以便弱网重试仍只创建一次 Visit。 */
public record CheckInVisitCommand(
        UUID appointmentId,
        Instant capturedAt,
        String deviceCommandId,
        String deviceId,
        VisitLocation location,
        boolean offline
) {
    public CheckInVisitCommand {
        appointmentId = Objects.requireNonNull(appointmentId, "appointmentId");
        capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
        deviceCommandId = text(deviceCommandId, "deviceCommandId", 160);
        deviceId = text(deviceId, "deviceId", 160);
        location = Objects.requireNonNull(location, "location");
    }

    static String text(String value, String name, int max) {
        value = Objects.requireNonNull(value, name).trim();
        if (value.isEmpty() || value.length() > max) throw new IllegalArgumentException(name + " is invalid");
        return value;
    }
}
