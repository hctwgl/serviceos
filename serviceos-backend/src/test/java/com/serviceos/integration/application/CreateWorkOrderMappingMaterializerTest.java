package com.serviceos.integration.application;

import com.serviceos.configuration.api.IntegrationMappingResult;
import com.serviceos.integration.spi.CreateWorkOrderMappedInbound;
import com.serviceos.shared.BusinessProblem;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateWorkOrderMappingMaterializerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mappingFieldsAuthoritativelyOverlayAdapterCompatMapping() {
        CreateWorkOrderMappedInbound adapter = seedAdapter("  ORD-RAW  ", "13800000000");
        IntegrationMappingResult mapping = new IntegrationMappingResult(
                "byd-create-v1",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "digest-aaa",
                "BYD_CPIM",
                "INBOUND",
                Map.of(
                        "externalOrderCode", "ORD-TRIMMED",
                        "customerMobile", "13900000000",
                        "provinceCode", "370000"),
                List.of("order: orderCode -> externalOrderCode [TRIM]"));

        CreateWorkOrderMappedInbound materialized = CreateWorkOrderMappingMaterializer.materialize(
                adapter, mapping, objectMapper);

        assertThat(materialized.externalOrderCode()).isEqualTo("ORD-TRIMMED");
        assertThat(materialized.customerMobile()).isEqualTo("13900000000");
        assertThat(materialized.provinceCode()).isEqualTo("370000");
        assertThat(materialized.brandCode()).isEqualTo(adapter.brandCode());
        assertThat(materialized.businessKey()).isEqualTo("BYD:INSTALL:ORD-TRIMMED");
        assertThat(materialized.mappingVersionId())
                .isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(new String(materialized.canonicalPayload()))
                .contains("digest-aaa")
                .contains("ORD-TRIMMED")
                .contains("mappingAssetVersionId");
    }

    @Test
    void blankMappedRequiredFieldFailsClosed() {
        CreateWorkOrderMappedInbound adapter = seedAdapter("ORD-1", "13800000000");
        IntegrationMappingResult mapping = new IntegrationMappingResult(
                "byd-create-v1",
                UUID.randomUUID(),
                "digest-bbb",
                "BYD_CPIM",
                "INBOUND",
                Map.of("externalOrderCode", "  "),
                List.of());

        assertThatThrownBy(() -> CreateWorkOrderMappingMaterializer.materialize(
                adapter, mapping, objectMapper))
                .isInstanceOf(BusinessProblem.class)
                .hasMessageContaining("blank required field");
    }

    @Test
    void rebuildBusinessKeyRequiresOrderCodeSuffix() {
        assertThatThrownBy(() -> CreateWorkOrderMappingMaterializer.rebuildBusinessKey(
                "BYD:INSTALL:OTHER", "ORD-1", "ORD-2"))
                .isInstanceOf(BusinessProblem.class)
                .hasMessageContaining("businessKey");
    }

    private static CreateWorkOrderMappedInbound seedAdapter(String orderCode, String mobile) {
        return new CreateWorkOrderMappedInbound(
                "BYD:INSTALL:" + orderCode.trim(),
                orderCode.trim(),
                "BYD",
                "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL",
                "370000",
                "370100",
                "370102",
                "姓名",
                mobile,
                "地址",
                "VIN123",
                LocalDateTime.of(2026, 7, 18, 10, 0),
                "adapter-mapping-v1",
                "{\"adapter\":true}".getBytes());
    }
}
