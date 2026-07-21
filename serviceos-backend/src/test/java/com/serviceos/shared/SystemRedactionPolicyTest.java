package com.serviceos.shared;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemRedactionPolicyTest {
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
    void businessDataIsRawByDefaultButCredentialsRemainProtected() {
        System.setProperty(SystemRedactionPolicy.PROPERTY_NAME, "false");

        String raw = "phone=13812345678 amount=199.50 password=hunter2";

        assertThat(SystemRedactionPolicy.redactFreeText(raw))
                .contains("13812345678", "199.50")
                .doesNotContain("hunter2")
                .contains("password=[REDACTED]");
        assertThat(SystemRedactionPolicy.personName("张三丰")).isEqualTo("张三丰");
        assertThat(SystemRedactionPolicy.phone("13812345678")).isEqualTo("13812345678");
        assertThat(SystemRedactionPolicy.address("山东省济南市历下区某某路 1 号"))
                .isEqualTo("山东省济南市历下区某某路 1 号");
    }

    @Test
    void enabledSwitchAppliesBusinessRedactionAcrossFreeTextAndTypedValues() {
        System.setProperty(SystemRedactionPolicy.PROPERTY_NAME, "true");

        String raw = "phone=13812345678 vin=LGXCE6CB1N0123456 安装地址=上海市示例路88号 amount=199.50";

        assertThat(SystemRedactionPolicy.redactFreeText(raw))
                .doesNotContain("13812345678", "LGXCE6CB1N0123456", "上海市示例路88号", "199.50")
                .contains("[REDACTED]");
        assertThat(SystemRedactionPolicy.personName("张三丰")).isEqualTo("张**");
        assertThat(SystemRedactionPolicy.phone("13812345678")).isEqualTo("*******5678");
        assertThat(SystemRedactionPolicy.address("山东省济南市历下区某某路 1 号"))
                .isEqualTo("山东省济南市***");
    }

    @Test
    void rejectsInvalidConfigurationInsteadOfSilentlyDisablingRedaction() {
        System.setProperty(SystemRedactionPolicy.PROPERTY_NAME, "yes");

        org.assertj.core.api.Assertions.assertThatThrownBy(SystemRedactionPolicy::businessDataRedactionEnabled)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be true or false");
    }
}
