package com.serviceos.bootstrap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataRedactorTest {
    @Test
    void redactsCredentialsIdentityContactAddressAndPriceValues() {
        String raw = """
                Authorization=Bearer top-secret-token-value; password=hunter2; \
                jwt=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMzgxMjM0NTY3OCJ9.abcdefghijklmnop; \
                phone=13812345678; vin=LGXCE6CB1N0123456; 安装地址=上海市示例路88号; amount=199.50
                """;

        String redacted = SensitiveDataRedactor.redact(raw);

        assertThat(redacted)
                .doesNotContain("top-secret-token-value", "hunter2", "eyJhbGci", "13812345678",
                        "LGXCE6CB1N0123456", "上海市示例路88号", "199.50")
                .contains("[REDACTED]");
    }

    @Test
    void leavesTraceAndCorrelationIdentifiersAvailableForDiagnostics() {
        String raw = "traceId=4bf92f3577b34da6a3ce929d0e0e4736 correlationId=corr-2026-1";

        assertThat(SensitiveDataRedactor.redact(raw)).isEqualTo(raw);
    }
}
