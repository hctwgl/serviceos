package com.serviceos.bootstrap;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.serviceos.shared.SystemRedactionPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedactingMessageConverterTest {
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
    void textLogMessageUsesTheSameGlobalPolicyAsEcsJson() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage())
                .thenReturn("phone=13812345678 amount=199.50 password=hunter2");
        RedactingMessageConverter converter = new RedactingMessageConverter();

        System.setProperty(SystemRedactionPolicy.PROPERTY_NAME, "false");
        assertThat(converter.convert(event))
                .contains("13812345678", "199.50")
                .doesNotContain("hunter2");

        System.setProperty(SystemRedactionPolicy.PROPERTY_NAME, "true");
        assertThat(converter.convert(event))
                .doesNotContain("13812345678", "199.50", "hunter2")
                .contains("[REDACTED]");
    }
}
