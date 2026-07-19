package com.serviceos.integration.spi;

/**
 * 远端状态查询 SPI。
 *
 * <p>仅用于 UNKNOWN Delivery 的权威探询；不得猜测成功。协议不支持时必须返回
 * {@link RemoteStatusQueryResult.NotSupported}，不得伪造成功 ACK。</p>
 */
public interface RemoteStatusQueryConnector {
    ConnectorIdentity identity();

    /** 是否认领该 connectorVersionId（出站 Delivery 上的版本）。 */
    boolean supportsConnectorVersion(String connectorVersionId);

    RemoteStatusQueryResult query(RemoteStatusQueryRequest request);
}
