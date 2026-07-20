package com.serviceos.sla.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * BUSINESS 截止与业务已用时长的纯函数计算。
 *
 * <p>相同 start、targetDurationSeconds、日历定义必须得到相同 deadline；不依赖墙钟与外部节假日服务。</p>
 */
final class BusinessCalendarDeadlineCalculator {
    private static final int MAX_DAY_WALK = 366 * 40;

    private BusinessCalendarDeadlineCalculator() {
    }

    static Instant addBusinessSeconds(Instant start, long businessSeconds, BusinessCalendar calendar) {
        if (start == null || calendar == null) {
            throw new IllegalArgumentException("start/calendar required");
        }
        if (businessSeconds < 1) {
            throw new IllegalArgumentException("businessSeconds must be positive");
        }
        ZonedDateTime cursor = start.atZone(calendar.zoneId());
        long remaining = businessSeconds;
        for (int dayWalk = 0; dayWalk < MAX_DAY_WALK && remaining > 0; dayWalk++) {
            LocalDate day = cursor.toLocalDate();
            for (BusinessCalendar.Window window : calendar.windowsFor(day)) {
                ZonedDateTime windowStart = day.atTime(window.start()).atZone(calendar.zoneId());
                ZonedDateTime windowEnd = day.atTime(window.end()).atZone(calendar.zoneId());
                if (!windowEnd.isAfter(cursor)) {
                    continue;
                }
                ZonedDateTime effectiveStart = cursor.isAfter(windowStart) ? cursor : windowStart;
                long available = Duration.between(effectiveStart, windowEnd).getSeconds();
                if (available <= 0) {
                    continue;
                }
                if (remaining <= available) {
                    return effectiveStart.plusSeconds(remaining).toInstant();
                }
                remaining -= available;
                cursor = windowEnd;
            }
            cursor = day.plusDays(1).atStartOfDay(calendar.zoneId());
        }
        throw new IllegalArgumentException("BUSINESS deadline exceeds calendar walk bound");
    }

    static long businessSecondsBetween(Instant start, Instant end, BusinessCalendar calendar) {
        if (start == null || end == null || calendar == null) {
            throw new IllegalArgumentException("start/end/calendar required");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end precedes start");
        }
        if (end.equals(start)) {
            return 0L;
        }
        ZonedDateTime cursor = start.atZone(calendar.zoneId());
        ZonedDateTime limit = end.atZone(calendar.zoneId());
        long total = 0L;
        for (int dayWalk = 0; dayWalk < MAX_DAY_WALK && cursor.isBefore(limit); dayWalk++) {
            LocalDate day = cursor.toLocalDate();
            for (BusinessCalendar.Window window : calendar.windowsFor(day)) {
                ZonedDateTime windowStart = day.atTime(window.start()).atZone(calendar.zoneId());
                ZonedDateTime windowEnd = day.atTime(window.end()).atZone(calendar.zoneId());
                if (!windowEnd.isAfter(cursor)) {
                    continue;
                }
                ZonedDateTime effectiveStart = cursor.isAfter(windowStart) ? cursor : windowStart;
                ZonedDateTime effectiveEnd = limit.isBefore(windowEnd) ? limit : windowEnd;
                if (!effectiveEnd.isAfter(effectiveStart)) {
                    continue;
                }
                total += Duration.between(effectiveStart, effectiveEnd).getSeconds();
                cursor = effectiveEnd;
                if (!cursor.isBefore(limit)) {
                    return total;
                }
            }
            cursor = day.plusDays(1).atStartOfDay(calendar.zoneId());
        }
        return total;
    }
}
