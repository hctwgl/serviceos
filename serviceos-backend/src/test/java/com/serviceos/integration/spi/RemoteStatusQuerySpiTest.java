package com.serviceos.integration.spi;

import com.serviceos.integration.application.RemoteStatusQueryConnectors;
import com.serviceos.integration.byd.application.BydRemoteStatusQueryConnector;
import com.serviceos.integration.geely.application.GeelyRemoteStatusQueryConnector;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteStatusQuerySpiTest {

    @Test
    void bydReportsNotSupported() {
        var connector = new BydRemoteStatusQueryConnector();
        assertThat(connector.supportsConnectorVersion("byd-cpim-submit-review-v7.3.1")).isTrue();
        RemoteStatusQueryResult result = connector.query(sampleRequest("byd-cpim-v7.3.1"));
        assertThat(result).isInstanceOf(RemoteStatusQueryResult.NotSupported.class);
    }

    @Test
    void geelyLocalStubDefaultsToStillUnknown() {
        var connector = new GeelyRemoteStatusQueryConnector("STILL_UNKNOWN");
        RemoteStatusQueryResult result = connector.query(
                sampleRequest("geely-haohan-submit-settlement-v1.3-local"));
        assertThat(result).isInstanceOf(RemoteStatusQueryResult.StillUnknown.class);
    }

    @Test
    void registryResolvesUniqueConnector() {
        var registry = new RemoteStatusQueryConnectors(List.of(
                new BydRemoteStatusQueryConnector(),
                new GeelyRemoteStatusQueryConnector("STILL_UNKNOWN")));
        assertThat(registry.requireForConnectorVersion("byd-cpim-v7.3.1"))
                .isInstanceOf(BydRemoteStatusQueryConnector.class);
        assertThatThrownBy(() -> registry.requireForConnectorVersion("unknown-oem"))
                .isInstanceOf(BusinessProblem.class)
                .extracting(ex -> ((BusinessProblem) ex).code())
                .isEqualTo(ProblemCode.RESOURCE_NOT_FOUND);
    }

    private static RemoteStatusQueryRequest sampleRequest(String connectorVersion) {
        return new RemoteStatusQueryRequest(
                "tenant-a",
                UUID.randomUUID(),
                connectorVersion,
                "ORDER-1",
                "BK-1",
                "a".repeat(64),
                "{\"x\":1}".getBytes(StandardCharsets.UTF_8));
    }
}
