package com.serviceos.integration.geely.application;

import com.serviceos.integration.spi.ConnectorIdentity;
import com.serviceos.integration.spi.RemoteStatusQueryConnector;
import com.serviceos.integration.spi.RemoteStatusQueryRequest;
import com.serviceos.integration.spi.RemoteStatusQueryResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 吉利远端查询本地 stub。
 *
 * <p>协议 7.21 可查询安装单详情，但无 Sandbox 时不得伪造成功。默认 STILL_UNKNOWN；
 * 仅测试可设置 {@code serviceos.integration.geely.remote-status-stub=ACCEPTED|REJECTED}。</p>
 */
@Component
public class GeelyRemoteStatusQueryConnector implements RemoteStatusQueryConnector {
    private static final ConnectorIdentity IDENTITY = new ConnectorIdentity(
            GeelyInboundCreateOrderService.CONNECTOR_CODE,
            GeelyInboundCreateOrderService.ADAPTER_VERSION);

    private final String stubMode;

    public GeelyRemoteStatusQueryConnector(
            @Value("${serviceos.integration.geely.remote-status-stub:STILL_UNKNOWN}") String stubMode
    ) {
        this.stubMode = stubMode == null ? "STILL_UNKNOWN" : stubMode.trim();
    }

    @Override
    public ConnectorIdentity identity() {
        return IDENTITY;
    }

    @Override
    public boolean supportsConnectorVersion(String connectorVersionId) {
        return connectorVersionId != null
                && (connectorVersionId.startsWith("geely-haohan")
                || GeelyInboundCreateOrderService.ADAPTER_VERSION.equals(connectorVersionId)
                || "geely-haohan-submit-settlement-v1.3-local".equals(connectorVersionId));
    }

    @Override
    public RemoteStatusQueryResult query(RemoteStatusQueryRequest request) {
        return switch (stubMode.toUpperCase()) {
            case "ACCEPTED" -> new RemoteStatusQueryResult.ConfirmedAccepted(
                    "GEELY-LOCAL-" + request.externalOrderCode(),
                    "LOCAL_STUB_CONFIRMED_ACCEPTED");
            case "REJECTED" -> new RemoteStatusQueryResult.ConfirmedRejected(
                    "GEELY-LOCAL-" + request.externalOrderCode(),
                    "LOCAL_STUB_CONFIRMED_REJECTED");
            default -> new RemoteStatusQueryResult.StillUnknown(
                    "GEELY_LOCAL_STUB_NO_AUTHORITATIVE_STATUS",
                    "Geely Sandbox unavailable; local stub refuses to invent remote status");
        };
    }
}
