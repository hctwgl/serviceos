package com.serviceos.integration.application;

import com.serviceos.configuration.api.IntegrationMappingResult;
import com.serviceos.integration.spi.UpdateWorkOrderMappedInbound;
import com.serviceos.integration.spi.UpdateWorkOrderRouteHint;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.Sha256;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateWorkOrderMappingMaterializerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void materializesRequiredFieldsAndDigestBusinessKey() {
        UpdateWorkOrderRouteHint route = new UpdateWorkOrderRouteHint(
                "BYD", "ORD-RAW", "BYD:INSTALL-UPDATE:ORD-RAW");
        IntegrationMappingResult mapping = new IntegrationMappingResult(
                "byd-update-v1",
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "digest-upd",
                "BYD_CPIM",
                "INBOUND",
                Map.of(
                        "externalOrderCode", "ORD-TRIM",
                        "customerName", "联系人",
                        "customerMobile", "13800000000",
                        "serviceAddress", "地址",
                        "provinceCode", "370000",
                        "cityCode", "370100",
                        "districtCode", "370102"),
                Map.of(),
                List.of("order mapped"));

        UpdateWorkOrderMappedInbound materialized = UpdateWorkOrderMappingMaterializer.materialize(
                route, mapping, objectMapper);

        assertThat(materialized.externalOrderCode()).isEqualTo("ORD-TRIM");
        assertThat(materialized.customerName()).isEqualTo("联系人");
        assertThat(materialized.districtCode()).isEqualTo("370102");
        assertThat(materialized.mappingVersionId())
                .isEqualTo("22222222-2222-2222-2222-222222222222");
        assertThat(materialized.updateDigest()).isEqualTo(Sha256.digest(materialized.canonicalPayload()));
        assertThat(materialized.businessKey())
                .isEqualTo("BYD:INSTALL-UPDATE:ORD-TRIM:" + materialized.updateDigest());
    }

    @Test
    void missingRequiredMappedFieldFailsClosed() {
        UpdateWorkOrderRouteHint route = new UpdateWorkOrderRouteHint(
                "BYD", "ORD-1", "BYD:INSTALL-UPDATE:ORD-1");
        IntegrationMappingResult mapping = new IntegrationMappingResult(
                "byd-update-v1",
                UUID.randomUUID(),
                "digest-b",
                "BYD_CPIM",
                "INBOUND",
                Map.of(
                        "externalOrderCode", "ORD-1",
                        "customerName", "n",
                        "customerMobile", "m",
                        "serviceAddress", "a",
                        "provinceCode", "370000",
                        "cityCode", "370100"),
                Map.of(),
                List.of());

        assertThatThrownBy(() -> UpdateWorkOrderMappingMaterializer.materialize(
                route, mapping, objectMapper))
                .isInstanceOf(BusinessProblem.class)
                .hasMessageContaining("districtCode");
    }
}
