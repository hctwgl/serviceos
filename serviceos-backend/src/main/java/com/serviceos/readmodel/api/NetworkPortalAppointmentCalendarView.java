package com.serviceos.readmodel.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Network Portal 预约日历读模型（今日/未来运营日节奏）。
 *
 * <p>不含客户 PII；运营时区固定 Asia/Shanghai。</p>
 */
public record NetworkPortalAppointmentCalendarView(
        UUID networkId,
        String timezone,
        LocalDate rangeStart,
        LocalDate rangeEnd,
        int totalAppointmentCount,
        boolean truncated,
        List<NetworkPortalAppointmentCalendarDay> days,
        Instant asOf
) {
    public NetworkPortalAppointmentCalendarView {
        Objects.requireNonNull(networkId, "networkId");
        Objects.requireNonNull(timezone, "timezone");
        Objects.requireNonNull(rangeStart, "rangeStart");
        Objects.requireNonNull(rangeEnd, "rangeEnd");
        days = List.copyOf(Objects.requireNonNull(days, "days"));
        Objects.requireNonNull(asOf, "asOf");
        if (totalAppointmentCount < 0) {
            throw new IllegalArgumentException("totalAppointmentCount must not be negative");
        }
        if (rangeEnd.isBefore(rangeStart)) {
            throw new IllegalArgumentException("rangeEnd must not be before rangeStart");
        }
    }
}
