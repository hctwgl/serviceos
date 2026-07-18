/**
 * 配置发布与运行时解析模块。业务实例只引用已发布的精确版本，禁止读取“最新配置”。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Configuration",
        allowedDependencies = {
                "shared",
                "identity::api",
                "authorization::api"
        }
)
package com.serviceos.configuration;
