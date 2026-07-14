/**
 * 派单模块：拥有 ServiceAssignment、容量权威计数与可靠激活 saga，不拥有 TaskAssignment。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Dispatch and Service Assignment",
        allowedDependencies = {
                "shared", "identity::api", "authorization::api",
                "audit::api", "reliability::api"
        }
)
package com.serviceos.dispatch;
