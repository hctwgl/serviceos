package com.serviceos.workorder.application;

import com.serviceos.shared.SystemRedactionPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderMaskedContactTest {
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
    void returnsRawBusinessValuesWhenGlobalRedactionIsDisabled() {
        System.setProperty(SystemRedactionPolicy.PROPERTY_NAME, "false");

        assertThat(DefaultWorkOrderQueryService.maskName("张三丰")).isEqualTo("张三丰");
        assertThat(DefaultWorkOrderQueryService.maskPhone("13812345678")).isEqualTo("13812345678");
        assertThat(DefaultWorkOrderQueryService.maskAddress("山东省济南市历下区某某路 1 号"))
                .isEqualTo("山东省济南市历下区某某路 1 号");
    }

    @Test
    void masksNamePhoneAndAddressWhenGlobalRedactionIsEnabled() {
        System.setProperty(SystemRedactionPolicy.PROPERTY_NAME, "true");

        assertThat(DefaultWorkOrderQueryService.maskName("张三丰")).isEqualTo("张**");
        assertThat(DefaultWorkOrderQueryService.maskPhone("13812345678")).isEqualTo("*******5678");
        assertThat(DefaultWorkOrderQueryService.maskAddress("山东省济南市历下区某某路 1 号"))
                .isEqualTo("山东省济南市***")
                .doesNotContain("某某路");
        assertThat(DefaultWorkOrderQueryService.maskPhone("5678")).isEqualTo("****");
    }
}
