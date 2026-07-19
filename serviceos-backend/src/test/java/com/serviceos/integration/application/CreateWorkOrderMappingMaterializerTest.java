package com.serviceos.integration.application;

import com.serviceos.configuration.api.IntegrationMappingResult;
import com.serviceos.integration.spi.CreateWorkOrderMappedInbound;
import com.serviceos.integration.spi.CreateWorkOrderRouteHint;
import com.serviceos.shared.BusinessProblem;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateWorkOrderMappingMaterializerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mappingFieldsAreSoleSourceWithoutRouteHintDomainFallback() {
        CreateWorkOrderRouteHint route = seedRoute("  ORD-RAW  ");
        IntegrationMappingResult mapping = new IntegrationMappingResult(
                "byd-create-v1",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "digest-aaa",
                "BYD_CPIM",
                "INBOUND",
                Map.of(
                        "externalOrderCode", "ORD-TRIMMED",
                        "brandCode", "BYD_OCEAN",
                        "serviceProductCode", "HOME_CHARGING_SURVEY_INSTALL",
                        "provinceCode", "370000",
                        "cityCode", "370100",
                        "districtCode", "370102",
                        "customerMobile", "13900000000"),
                Map.of(),
                List.of("order: orderCode -> externalOrderCode [TRIM]"));

        CreateWorkOrderMappedInbound materialized = CreateWorkOrderMappingMaterializer.materialize(
                route, mapping, objectMapper);

        assertThat(materialized.externalOrderCode()).isEqualTo("ORD-TRIMMED");
        assertThat(materialized.customerMobile()).isEqualTo("13900000000");
        assertThat(materialized.provinceCode()).isEqualTo("370000");
        assertThat(materialized.brandCode()).isEqualTo("BYD_OCEAN");
        assertThat(materialized.customerName()).isNull();
        assertThat(materialized.serviceAddress()).isNull();
        assertThat(materialized.vehicleVin()).isNull();
        assertThat(materialized.dispatchedAt()).isNull();
        assertThat(materialized.businessKey()).isEqualTo("BYD:INSTALL:ORD-TRIMMED");
        assertThat(materialized.mappingVersionId())
                .isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(new String(materialized.canonicalPayload()))
                .contains("digest-aaa")
                .contains("ORD-TRIMMED")
                .contains("mappingAssetVersionId");
    }

    @Test
    void missingRequiredMappedFieldFailsClosed() {
        CreateWorkOrderRouteHint route = seedRoute("ORD-1");
        IntegrationMappingResult mapping = new IntegrationMappingResult(
                "byd-create-v1",
                UUID.randomUUID(),
                "digest-bbb",
                "BYD_CPIM",
                "INBOUND",
                Map.of(
                        "externalOrderCode", "ORD-1",
                        "brandCode", "BYD_OCEAN",
                        "serviceProductCode", "HOME_CHARGING_SURVEY_INSTALL",
                        "provinceCode", "370000",
                        "cityCode", "370100"),
                Map.of(),
                List.of());

        assertThatThrownBy(() -> CreateWorkOrderMappingMaterializer.materialize(
                route, mapping, objectMapper))
                .isInstanceOf(BusinessProblem.class)
                .hasMessageContaining("missing required field: districtCode");
    }

    @Test
    void blankMappedRequiredFieldFailsClosed() {
        CreateWorkOrderRouteHint route = seedRoute("ORD-1");
        IntegrationMappingResult mapping = new IntegrationMappingResult(
                "byd-create-v1",
                UUID.randomUUID(),
                "digest-bbb",
                "BYD_CPIM",
                "INBOUND",
                Map.of("externalOrderCode", "  "),
                Map.of(),
                List.of());

        assertThatThrownBy(() -> CreateWorkOrderMappingMaterializer.materialize(
                route, mapping, objectMapper))
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

    private static CreateWorkOrderRouteHint seedRoute(String orderCode) {
        String trimmed = orderCode.trim();
        return new CreateWorkOrderRouteHint(
                "BYD:INSTALL:" + trimmed,
                trimmed,
                "BYD",
                "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL",
                "370000");
    }
}
