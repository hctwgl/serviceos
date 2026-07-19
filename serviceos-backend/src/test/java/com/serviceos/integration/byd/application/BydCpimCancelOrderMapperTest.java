package com.serviceos.integration.byd.application;

import com.serviceos.integration.spi.CancelWorkOrderMappedInbound;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BydCpimCancelOrderMapperTest {
    private final BydCpimCancelOrderMapper mapper = new BydCpimCancelOrderMapper();
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void mapsStrictCancelPayload() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("orderCode", "ORDER-9");
        source.put("cancelDate", "2026-07-19 12:00:00");
        source.put("cancelReason", "用户主动取消");
        CancelWorkOrderMappedInbound mapped = mapper.map(source, objectMapper);
        assertThat(mapped.externalOrderCode()).isEqualTo("ORDER-9");
        assertThat(mapped.createBusinessKey()).isEqualTo("BYD:INSTALL:ORDER-9");
        assertThat(mapped.businessKey()).isEqualTo("BYD:CANCEL:ORDER-9:2026-07-19 12:00:00");
        assertThat(mapped.reasonCode()).isEqualTo("EXTERNAL_USER_CANCEL");
    }

    @Test
    void rejectsUnknownFieldAndBadDate() {
        Map<String, Object> unknown = new LinkedHashMap<>();
        unknown.put("orderCode", "ORDER-9");
        unknown.put("cancelDate", "2026-07-19 12:00:00");
        unknown.put("cancelReason", "x");
        unknown.put("extra", "nope");
        assertThatThrownBy(() -> mapper.map(unknown, objectMapper))
                .isInstanceOf(IllegalArgumentException.class);

        Map<String, Object> badDate = new LinkedHashMap<>();
        badDate.put("orderCode", "ORDER-9");
        badDate.put("cancelDate", "2026/07/19");
        badDate.put("cancelReason", "x");
        assertThatThrownBy(() -> mapper.map(badDate, objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cancelDate");
    }
}
