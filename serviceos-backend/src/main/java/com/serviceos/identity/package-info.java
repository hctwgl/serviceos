/**
 * 身份模块：把受信 OIDC/JWT 主体映射为 ServiceOS 稳定 CurrentPrincipal。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Identity",
        allowedDependencies = {"shared", "audit :: api", "reliability :: api"}
)
package com.serviceos.identity;
