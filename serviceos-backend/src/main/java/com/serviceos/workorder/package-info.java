/**
 * 工单核心模块：拥有 WorkOrder 权威事实、外部订单业务幂等与配置版本锁定。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Work Order",
        allowedDependencies = {"shared", "reliability::api", "audit::api", "project::api"}
)
package com.serviceos.workorder;
