/** 动态表单模块：只解析 Task 冻结的精确配置版本，不读取当前最新配置。 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Forms",
        allowedDependencies = {
                "audit::api", "authorization::api", "configuration::api", "identity::api", "jooq",
                "dispatch::api", "network::api", "reliability::api", "shared", "task::api", "workorder::api"
        })
package com.serviceos.forms;
