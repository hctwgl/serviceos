package com.serviceos.bootstrap;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresClockConfigurationTest {

    @Test
    void productionClockOnlyEmitsPostgresSafeMicrosecondInstants() {
        Clock clock = new PostgresClockConfiguration().postgresClock();

        for (int index = 0; index < 1_000; index++) {
            Instant now = clock.instant();
            assertThat(now.getNano() % 1_000).isZero();
        }
    }
}
