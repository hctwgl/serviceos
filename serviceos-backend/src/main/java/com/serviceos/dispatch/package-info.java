/**
 * 派单模块：拥有 ServiceAssignment、容量权威计数与可靠激活 saga；通过 Task 公共命令
 * 冻结师傅候选执行权，不拥有 TaskAssignment 持久化事实。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Dispatch and Service Assignment",
        allowedDependencies = {
                "shared", "identity::api", "authorization::api",
                "audit::api", "reliability::api", "reliability::spi", "task::api",
                "network::api", "project::api", "configuration::api", "workorder::api"
        }
)
package com.serviceos.dispatch;
