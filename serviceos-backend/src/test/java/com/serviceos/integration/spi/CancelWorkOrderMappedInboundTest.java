package com.serviceos.integration.spi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancelWorkOrderMappedInboundTest {

    @Test
    void acceptsValidCancelMappedInbound() {
        CancelWorkOrderMappedInbound mapped = new CancelWorkOrderMappedInbound(
                "BYD:CANCEL:ORDER-1:2026-07-19 10:00:00",
                "BYD:INSTALL:ORDER-1",
                "ORDER-1",
                "BYD",
                "EXTERNAL_USER_CANCEL",
                "BYD:用户取消",
                "mapping-v1",
                "{\"orderCode\":\"ORDER-1\"}".getBytes());
        assertThat(mapped.businessKey()).startsWith("BYD:CANCEL:");
        assertThat(CancelWorkOrderMappedInbound.MESSAGE_TYPE_CANCEL_WORK_ORDER)
                .isEqualTo("CANCEL_WORK_ORDER");
    }

    @Test
    void rejectsBlankReasonCode() {
        assertThatThrownBy(() -> new CancelWorkOrderMappedInbound(
                "bk", "create-bk", "ORDER-1", "BYD", " ", null, "mapping-v1", "{}".getBytes()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reasonCode");
    }
}
