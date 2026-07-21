package com.serviceos.readmodel.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Network 预约日历单日桶（运营日 Asia/Shanghai）。
 */
public record NetworkPortalAppointmentCalendarDay(
        LocalDate date,
        int appointmentCount,
        List<NetworkPortalWorkbenchAppointmentItem> items
) {
    public NetworkPortalAppointmentCalendarDay {
        Objects.requireNonNull(date, "date");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (appointmentCount < 0) {
            throw new IllegalArgumentException("appointmentCount must not be negative");
        }
        if (appointmentCount != items.size()) {
            throw new IllegalArgumentException("appointmentCount must equal items.size()");
        }
    }
}
