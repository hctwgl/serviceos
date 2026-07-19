package com.serviceos.integration.application;

import com.serviceos.configuration.api.IntegrationMappingResult;
import com.serviceos.integration.spi.CancelWorkOrderMappedInbound;
import com.serviceos.integration.spi.CancelWorkOrderRouteHint;
import com.serviceos.shared.BusinessProblem;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancelWorkOrderMappingMaterializerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void materializesReasonAndRebuildsBusinessKeys() {
        CancelWorkOrderRouteHint route = new CancelWorkOrderRouteHint(
                "BYD",
                "ORD-RAW",
                "BYD:INSTALL:ORD-RAW",
                "BYD:CANCEL:ORD-RAW",
                "2026-07-19 15:30:00");
        IntegrationMappingResult mapping = new IntegrationMappingResult(
                "byd-cancel-v1",
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "digest-cxl",
                "BYD_CPIM",
                "INBOUND",
                Map.of(
                        "externalOrderCode", "ORD-TRIM",
                        "reasonCode", "EXTERNAL_USER_CANCEL",
                        "approvalRef", "用户主动取消"),
                Map.of(),
                List.of("reason constant"));

        CancelWorkOrderMappedInbound materialized = CancelWorkOrderMappingMaterializer.materialize(
                route, mapping, objectMapper);

        assertThat(materialized.externalOrderCode()).isEqualTo("ORD-TRIM");
        assertThat(materialized.reasonCode()).isEqualTo("EXTERNAL_USER_CANCEL");
        assertThat(materialized.approvalRef()).isEqualTo("用户主动取消");
        assertThat(materialized.createBusinessKey()).isEqualTo("BYD:INSTALL:ORD-TRIM");
        assertThat(materialized.businessKey()).isEqualTo("BYD:CANCEL:ORD-TRIM:2026-07-19 15:30:00");
        assertThat(materialized.mappingVersionId())
                .isEqualTo("33333333-3333-3333-3333-333333333333");
    }

    @Test
    void missingReasonCodeFailsClosed() {
        CancelWorkOrderRouteHint route = new CancelWorkOrderRouteHint(
                "BYD", "ORD-1", "BYD:INSTALL:ORD-1", "BYD:CANCEL:ORD-1", "suffix");
        IntegrationMappingResult mapping = new IntegrationMappingResult(
                "byd-cancel-v1",
                UUID.randomUUID(),
                "digest",
                "BYD_CPIM",
                "INBOUND",
                Map.of("externalOrderCode", "ORD-1"),
                Map.of(),
                List.of());

        assertThatThrownBy(() -> CancelWorkOrderMappingMaterializer.materialize(
                route, mapping, objectMapper))
                .isInstanceOf(BusinessProblem.class)
                .hasMessageContaining("reasonCode");
    }
}
