/**
 * 跨模块只读投影：消费公开事件构建工作区、队列和时间线，不拥有或回写领域事实。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Application Read Model",
        allowedDependencies = {
                "shared", "identity::api", "authorization::api", "audit::api",
                "reliability::api", "reliability::spi",
                "workorder::api", "workflow::api", "task::api", "integration::api",
                "operations::api", "evidence::api", "sla::api",
                "fieldwork::api", "appointment::api", "forms::api", "dispatch::api",
                "network::api", "project::api"
        }
)
package com.serviceos.readmodel;
