/**
 * 外部系统适配模块：负责协议认证、报文校验、反腐层映射和可靠收发。
 *
 * <p>本模块不得把车企字段直接扩散到核心领域；外部协议变化必须止于适配器边界。</p>
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "External Integration",
        allowedDependencies = {
                "shared", "reliability::api", "audit::api", "project::api",
                "configuration::api", "workorder::api"
        }
)
package com.serviceos.integration;
