/**
 * 可靠性模块：拥有幂等记录、Outbox/Inbox 与通用异步执行事实。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Reliability",
        allowedDependencies = "shared"
)
package com.serviceos.reliability;
