package com.serviceos.integration.geely.application;

import com.serviceos.integration.geely.api.GeelyCreateOrderPayload;
import com.serviceos.integration.spi.CreateWorkOrderMappedInbound;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeelyCreateOrderMapperTest {
    @Test
    void mapsRequiredFieldsToCanonicalInbound() {
        GeelyCreateOrderPayload payload = new GeelyCreateOrderPayload(
                "IN2025081415311100001",
                "HW2025081415292700001",
                "2025-08-14 15:31:11",
                "2025-08-14 15:00:00",
                1,
                "车主",
                "13900000000",
                "联系人",
                "13800000000",
                "370000",
                "370100",
                "370102",
                "示例小区 1 号",
                null,
                "GEELY",
                "星愿",
                "VIN123456789012345",
                1,
                null,
                0,
                List.of(new GeelyCreateOrderPayload.Product("7kW", 7.0, "PKG-A")));
        CreateWorkOrderMappedInbound inbound = GeelyCreateOrderMapper.map(
                payload, JsonMapper.builder().build());
        assertThat(inbound.externalOrderCode()).isEqualTo("IN2025081415311100001");
        assertThat(inbound.clientCode()).isEqualTo("GEELY");
        assertThat(inbound.brandCode()).isEqualTo("GEELY");
        assertThat(inbound.provinceCode()).isEqualTo("370000");
        assertThat(inbound.customerName()).isEqualTo("联系人");
        assertThat(inbound.mappingVersionId()).isEqualTo(GeelyCreateOrderMapper.MAPPING_VERSION);
        assertThat(inbound.dispatchedAt()).isNotNull();
    }
}
