/**
 * 应用装配与协议级横切能力。该模块可以依赖共享值对象，但业务模块不得反向依赖它。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Application Bootstrap",
        allowedDependencies = "shared"
)
package com.serviceos.bootstrap;
