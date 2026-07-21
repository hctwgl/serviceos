package com.serviceos.bootstrap;

import com.serviceos.shared.SystemRedactionPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataRedactorTest {
    private String previousValue;

    @BeforeEach
    void rememberSwitch() {
        previousValue = System.getProperty(SystemRedactionPolicy.PROPERTY_NAME);
    }

    @AfterEach
    void restoreSwitch() {
        if (previousValue == null) {
            System.clearProperty(SystemRedactionPolicy.PROPERTY_NAME);
        } else {
            System.setProperty(SystemRedactionPolicy.PROPERTY_NAME, previousValue);
        }
    }

    @Test
    void alwaysProtectsCredentialsEvenWhenBusinessRedactionIsDisabled() {
        System.setProperty(SystemRedactionPolicy.PROPERTY_NAME, "false");
        String raw = """
                Authorization=Bearer top-secret-token-value; password=hunter2; \
                jwt=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMzgxMjM0NTY3OCJ9.abcdefghijklmnop; \
                phone=13812345678; vin=LGXCE6CB1N0123456; 安装地址=上海市示例路88号; amount=199.50
                """;

        String redacted = SensitiveDataRedactor.redact(raw);

        assertThat(redacted)
                .doesNotContain("top-secret-token-value", "hunter2", "eyJhbGci")
                .contains("13812345678", "LGXCE6CB1N0123456", "上海市示例路88号", "199.50", "[REDACTED]");
    }

    @Test
    void redactsBusinessValuesWhenGlobalSwitchIsEnabled() {
        System.setProperty(SystemRedactionPolicy.PROPERTY_NAME, "true");
        String raw = """
                phone=13812345678; vin=LGXCE6CB1N0123456; 安装地址=上海市示例路88号; amount=199.50
                """;

        assertThat(SensitiveDataRedactor.redact(raw))
                .doesNotContain("13812345678", "LGXCE6CB1N0123456", "上海市示例路88号", "199.50")
                .contains("[REDACTED]");
    }

    @Test
    void leavesTraceAndCorrelationIdentifiersAvailableForDiagnostics() {
        System.setProperty(SystemRedactionPolicy.PROPERTY_NAME, "true");
        String raw = "traceId=4bf92f3577b34da6a3ce929d0e0e4736 correlationId=corr-2026-1";

        assertThat(SensitiveDataRedactor.redact(raw)).isEqualTo(raw);
    }
}
