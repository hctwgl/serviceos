package com.serviceos.integration.byd.application;

import com.serviceos.integration.spi.ConnectorIdentity;
import com.serviceos.integration.spi.RemoteStatusQueryConnector;
import com.serviceos.integration.spi.RemoteStatusQueryRequest;
import com.serviceos.integration.spi.RemoteStatusQueryResult;
import org.springframework.stereotype.Component;

/**
 * BYD CPIM 不提供权威远端状态查询；失败关闭为 NOT_SUPPORTED，不得猜 ACK。
 */
@Component
public class BydRemoteStatusQueryConnector implements RemoteStatusQueryConnector {
    private static final ConnectorIdentity IDENTITY = new ConnectorIdentity(
            "BYD_CPIM", "byd-cpim-v7.3.1");

    @Override
    public ConnectorIdentity identity() {
        return IDENTITY;
    }

    @Override
    public boolean supportsConnectorVersion(String connectorVersionId) {
        return connectorVersionId != null
                && connectorVersionId.startsWith("byd-cpim");
    }

    @Override
    public RemoteStatusQueryResult query(RemoteStatusQueryRequest request) {
        return new RemoteStatusQueryResult.NotSupported(
                "BYD_CPIM_NO_REMOTE_STATUS_QUERY",
                "BYD CPIM v7.3.1 does not expose an authoritative remote status query for UNKNOWN deliveries");
    }
}
