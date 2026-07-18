package com.serviceos.integration.application;

import com.serviceos.integration.spi.CreateWorkOrderMappedInbound;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InboundCreateWorkOrderPipelineTest {

    @Test
    void mappedInboundRequiresBusinessExternalOrderAndCanonicalPayload() {
        byte[] payload = "{\"ok\":true}".getBytes();
        CreateWorkOrderMappedInbound inbound = new CreateWorkOrderMappedInbound(
                "BYD:INSTALL:ORDER-1",
                "ORDER-1",
                "BYD",
                "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL",
                "370000",
                "370100",
                "370102",
                "姓名",
                "13800000000",
                "地址",
                "VIN123",
                LocalDateTime.of(2026, 7, 18, 10, 0),
                "mapping-v1",
                payload);
        assertThat(inbound.businessKey()).isEqualTo("BYD:INSTALL:ORDER-1");
        assertThat(inbound.externalOrderCode()).isEqualTo("ORDER-1");
        assertThat(inbound.canonicalPayload()).isEqualTo(payload);
        assertThat(CreateWorkOrderMappedInbound.MESSAGE_TYPE_CREATE_WORK_ORDER)
                .isEqualTo("CREATE_WORK_ORDER");
    }

    @Test
    void mappedInboundRejectsBlankBusinessKey() {
        assertThatThrownBy(() -> new CreateWorkOrderMappedInbound(
                " ",
                "ORDER-1",
                "BYD",
                "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL",
                "370000",
                "370100",
                "370102",
                null,
                null,
                null,
                null,
                null,
                "mapping-v1",
                "{\"a\":1}".getBytes()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("businessKey");
    }
}
