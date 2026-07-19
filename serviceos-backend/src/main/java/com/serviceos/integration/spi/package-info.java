/**
 * 外部连接器扩展 SPI。
 *
 * <p>入站：OEM 适配器完成协议验签与反腐映射后，通过本包契约进入通用入站管道。
 * 出站：OEM 适配器实现 {@link OutboundSubmissionConnector}，由通用出站管道编排 attempt/ACK。
 * 远端查询：OEM 适配器实现 {@link RemoteStatusQueryConnector}；协议不支持时必须
 * {@code NotSupported}，不得猜 ACK。核心履约模块不得依赖具体 OEM 适配包。</p>
 */
@org.springframework.modulith.NamedInterface("spi")
package com.serviceos.integration.spi;
