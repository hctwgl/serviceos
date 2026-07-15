package com.serviceos.bootstrap;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresClockConfigurationTest {

    @Test
    void postgresSafeClockTruncatesNanosecondsToExactMicrosecondTick() {
        Clock source = Clock.fixed(
                Instant.parse("2026-07-15T14:00:00.123456789Z"), ZoneOffset.UTC);

        Clock clock = PostgresClockConfiguration.postgresSafeClock(source);

        assertThat(clock.instant()).isEqualTo(
                Instant.parse("2026-07-15T14:00:00.123456Z"));
    }

    @Test
    void postProcessorOnlyWrapsTheExistingProductionSystemClock() {
        var processor = PostgresClockConfiguration.postgresClockPrecisionPostProcessor();
        Clock source = Clock.fixed(
                Instant.parse("2026-07-15T14:00:00.987654321Z"), ZoneOffset.UTC);

        Object processedSystemClock = processor.postProcessAfterInitialization(source, "systemClock");
        Object untouchedMutableClock = processor.postProcessAfterInitialization(source, "mutableClock");

        assertThat(processedSystemClock).isInstanceOf(Clock.class);
        assertThat(((Clock) processedSystemClock).instant()).isEqualTo(
                Instant.parse("2026-07-15T14:00:00.987654Z"));
        assertThat(untouchedMutableClock).isSameAs(source);
    }
}
