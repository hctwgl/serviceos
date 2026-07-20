package com.serviceos.sla.application;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M369：BUSINESS 截止与业务已用时长的纯函数证明。 */
class BusinessCalendarDeadlineCalculatorTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void weekdayWindowAddsBusinessSecondsWithoutWallClockPadding() {
        BusinessCalendar calendar = sampleCalendar("""
                []
                """, """
                []
                """);
        // 周三 10:00 CST = 02:00Z；8 业务小时应落到当日 18:00 CST。
        Instant start = Instant.parse("2026-07-15T02:00:00Z");
        Instant deadline = BusinessCalendarDeadlineCalculator.addBusinessSeconds(start, 8 * 3600, calendar);
        assertThat(deadline).isEqualTo(Instant.parse("2026-07-15T10:00:00Z"));
    }

    @Test
    void skipsWeekendAndHolidayWhenCrossingNonBusinessDays() {
        BusinessCalendar calendar = sampleCalendar("""
                ["2026-07-17"]
                """, """
                []
                """);
        // 周五（节假日）17:00 CST 起算 2 业务小时：当日无窗，跳过周末，周一 09:00 起计 → 11:00 CST。
        Instant start = Instant.parse("2026-07-17T09:00:00Z");
        Instant deadline = BusinessCalendarDeadlineCalculator.addBusinessSeconds(start, 2 * 3600, calendar);
        assertThat(deadline.atZone(SHANGHAI).toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(deadline).isEqualTo(Instant.parse("2026-07-20T03:00:00Z"));
    }

    @Test
    void extraWorkdayReusesMondayWindows() {
        BusinessCalendar calendar = sampleCalendar("""
                []
                """, """
                ["2026-07-18"]
                """);
        // 周六调休：复用周一 09-18；从 09:00 CST 起 1 小时 → 10:00 CST。
        Instant start = Instant.parse("2026-07-18T01:00:00Z");
        Instant deadline = BusinessCalendarDeadlineCalculator.addBusinessSeconds(start, 3600, calendar);
        assertThat(deadline).isEqualTo(Instant.parse("2026-07-18T02:00:00Z"));
    }

    @Test
    void businessSecondsBetweenCountsOnlyOpenWindows() {
        BusinessCalendar calendar = sampleCalendar("[]", "[]");
        Instant start = Instant.parse("2026-07-15T02:00:00Z"); // Wed 10:00 CST
        Instant end = Instant.parse("2026-07-16T02:00:00Z"); // Thu 10:00 CST
        // Wed 10-18 = 8h, Thu 09-10 = 1h → 9h
        assertThat(BusinessCalendarDeadlineCalculator.businessSecondsBetween(start, end, calendar))
                .isEqualTo(9 * 3600L);
    }

    @Test
    void rejectsNonPositiveDuration() {
        BusinessCalendar calendar = sampleCalendar("[]", "[]");
        assertThatThrownBy(() -> BusinessCalendarDeadlineCalculator.addBusinessSeconds(
                Instant.parse("2026-07-15T02:00:00Z"), 0, calendar))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("businessSeconds");
    }

    private static BusinessCalendar sampleCalendar(String holidaysJson, String extrasJson) {
        String json = """
                {"calendarKey":"cn.workdays.sample","version":"1.0.0","timeZone":"Asia/Shanghai",
                 "weeklyWindows":[
                   {"dayOfWeek":"MONDAY","start":"09:00","end":"18:00"},
                   {"dayOfWeek":"TUESDAY","start":"09:00","end":"18:00"},
                   {"dayOfWeek":"WEDNESDAY","start":"09:00","end":"18:00"},
                   {"dayOfWeek":"THURSDAY","start":"09:00","end":"18:00"},
                   {"dayOfWeek":"FRIDAY","start":"09:00","end":"18:00"}],
                 "holidays":%s,
                 "extraWorkdays":%s}
                """.formatted(holidaysJson.trim(), extrasJson.trim());
        return BusinessCalendar.parse(MAPPER.readTree(json));
    }
}
