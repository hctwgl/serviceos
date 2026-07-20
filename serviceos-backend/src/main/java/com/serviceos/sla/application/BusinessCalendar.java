package com.serviceos.sla.application;

import tools.jackson.databind.JsonNode;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * M369 Bundle 冻结业务日历。只描述工作窗与例外日；截止计算见 {@link BusinessCalendarDeadlineCalculator}。
 */
record BusinessCalendar(
        String calendarKey,
        String version,
        ZoneId zoneId,
        Map<DayOfWeek, List<Window>> weeklyWindows,
        Set<LocalDate> holidays,
        Set<LocalDate> extraWorkdays
) {
    BusinessCalendar {
        Objects.requireNonNull(calendarKey, "calendarKey");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(zoneId, "zoneId");
        weeklyWindows = Map.copyOf(weeklyWindows);
        holidays = Set.copyOf(holidays);
        extraWorkdays = Set.copyOf(extraWorkdays);
        if (weeklyWindows.isEmpty()) {
            throw new IllegalArgumentException("weeklyWindows must not be empty");
        }
        for (List<Window> windows : weeklyWindows.values()) {
            for (Window window : windows) {
                if (!window.end().isAfter(window.start())) {
                    throw new IllegalArgumentException("calendar window end must be after start");
                }
            }
        }
    }

    static BusinessCalendar parse(JsonNode root) {
        String key = text(root, "calendarKey");
        String version = text(root, "version");
        ZoneId zone;
        try {
            zone = ZoneId.of(text(root, "timeZone"));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("calendar timeZone is invalid", exception);
        }
        Map<DayOfWeek, List<Window>> weekly = new EnumMap<>(DayOfWeek.class);
        for (JsonNode node : root.path("weeklyWindows")) {
            DayOfWeek day = DayOfWeek.valueOf(text(node, "dayOfWeek"));
            Window window = new Window(LocalTime.parse(text(node, "start")), LocalTime.parse(text(node, "end")));
            weekly.computeIfAbsent(day, ignored -> new ArrayList<>()).add(window);
        }
        for (List<Window> windows : weekly.values()) {
            windows.sort((left, right) -> left.start().compareTo(right.start()));
        }
        Set<LocalDate> holidays = dates(root.path("holidays"));
        Set<LocalDate> extras = dates(root.path("extraWorkdays"));
        return new BusinessCalendar(key, version, zone, weekly, holidays, extras);
    }

    /** 某日有效工作窗；节假日无班，调休日复用周一窗（样例语义，ADR-090 D2-R）。 */
    List<Window> windowsFor(LocalDate day) {
        boolean holiday = holidays.contains(day);
        boolean extra = extraWorkdays.contains(day);
        if (holiday && !extra) {
            return List.of();
        }
        DayOfWeek dow = day.getDayOfWeek();
        if (extra && (holiday || dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY)) {
            return weeklyWindows.getOrDefault(DayOfWeek.MONDAY, List.of());
        }
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return List.of();
        }
        return weeklyWindows.getOrDefault(dow, List.of());
    }

    private static Set<LocalDate> dates(JsonNode array) {
        Set<LocalDate> dates = new HashSet<>();
        if (array == null || !array.isArray()) {
            return dates;
        }
        for (JsonNode node : array) {
            dates.add(LocalDate.parse(node.asText().trim()));
        }
        return dates;
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    record Window(LocalTime start, LocalTime end) {
    }
}
