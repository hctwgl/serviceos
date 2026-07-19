package com.serviceos.integration.byd.application;

import com.serviceos.integration.spi.UpdateWorkOrderMappedInbound;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BydCpimUpdateOrderMapperTest {
    private final BydCpimUpdateOrderMapper mapper = new BydCpimUpdateOrderMapper();
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void mapsStrictUpdatePayload() {
        Map<String, Object> source = valid();
        UpdateWorkOrderMappedInbound mapped = mapper.map(source, objectMapper);
        assertThat(mapped.externalOrderCode()).isEqualTo("ORDER-U1");
        assertThat(mapped.businessKey()).startsWith("BYD:INSTALL-UPDATE:ORDER-U1:");
        assertThat(mapped.updateDigest()).hasSize(64);
        assertThat(mapped.serviceAddress()).isEqualTo("山东省济南市历下区新地址");
    }

    @Test
    void rejectsUnknownField() {
        Map<String, Object> source = valid();
        source.put("extra", "x");
        assertThatThrownBy(() -> mapper.map(source, objectMapper))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<String, Object> valid() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("orderCode", "ORDER-U1");
        source.put("contactName", "新联系人");
        source.put("contactMobile", "13900000000");
        source.put("contactAddress", "山东省济南市历下区新地址");
        source.put("provinceCode", "370000");
        source.put("cityCode", "370100");
        source.put("areaCode", "370102");
        return source;
    }
}
