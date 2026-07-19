package com.serviceos.workorder.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderMaskedContactTest {
    @Test
    void masksNamePhoneAndAddressWithoutLeakingFullValues() {
        assertThat(DefaultWorkOrderQueryService.maskName("张三丰")).isEqualTo("张**");
        assertThat(DefaultWorkOrderQueryService.maskPhone("13812345678")).isEqualTo("*******5678");
        assertThat(DefaultWorkOrderQueryService.maskAddress("山东省济南市历下区某某路 1 号"))
                .isEqualTo("山东省济南市***")
                .doesNotContain("某某路");
        assertThat(DefaultWorkOrderQueryService.maskPhone("5678")).isEqualTo("****");
    }
}
