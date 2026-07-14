package com.serviceos.appointment.api;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/** 预约窗口使用绝对时间保存，同时冻结业务展示时区。 */
public record AppointmentWindow(
        Instant start,
        Instant end,
        String timezone,
        int estimatedDurationMinutes
) {
    public AppointmentWindow {
        start = Objects.requireNonNull(start, "start");
        end = Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) throw new IllegalArgumentException("appointment end must be after start");
        timezone = Objects.requireNonNull(timezone, "timezone").trim();
        if (timezone.isEmpty() || timezone.length() > 80) {
            throw new IllegalArgumentException("timezone is invalid");
        }
        ZoneId.of(timezone);
        if (estimatedDurationMinutes < 1 || estimatedDurationMinutes > 1440) {
            throw new IllegalArgumentException("estimatedDurationMinutes must be between 1 and 1440");
        }
    }
}
