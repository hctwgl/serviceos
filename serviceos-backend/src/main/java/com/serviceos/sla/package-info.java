/** SLA 时钟模块：拥有策略锁定实例、时钟片段、到期里程碑和可恢复的超时事实。 */
@org.springframework.modulith.ApplicationModule(
        displayName = "SLA Clock",
        allowedDependencies = {
                "shared", "jooq", "configuration::api", "task::api", "workorder::api",
                "identity::api", "authorization::api",
                "reliability::api", "reliability::spi"
        }
)
package com.serviceos.sla;
