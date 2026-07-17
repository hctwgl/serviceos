/**
 * 授权模块：执行 capability、租户与资源数据范围判定，并可靠审计拒绝结果。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Authorization",
        allowedDependencies = {"shared", "identity::api", "audit::api", "organization :: api", "network :: api"}
)
package com.serviceos.authorization;
