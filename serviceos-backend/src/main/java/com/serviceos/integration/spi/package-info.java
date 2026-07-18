/**
 * 外部连接器扩展 SPI。
 *
 * <p>OEM 适配器完成协议验签与反腐映射后，只能通过本包契约进入通用入站管道；
 * 核心履约模块不得依赖具体 OEM 适配包。</p>
 */
@org.springframework.modulith.NamedInterface("spi")
package com.serviceos.integration.spi;
