package com.serviceos.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdsTest {
    @Test
    void acceptsSafeCallerValue() {
        assertThat(CorrelationIds.normalizeOrCreate("order/2026:corr-1"))
                .isEqualTo("order/2026:corr-1");
    }

    @Test
    void replacesHeaderInjectionAndOversizedValues() {
        assertThat(CorrelationIds.normalizeOrCreate("safe\r\nX-Forged: true"))
                .matches("[0-9a-f-]{36}");
        assertThat(CorrelationIds.normalizeOrCreate("a".repeat(129)))
                .matches("[0-9a-f-]{36}");
    }
}
