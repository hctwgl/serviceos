/**
 * 项目目录模块：拥有 Client、Brand、Project、ServiceProduct 及其有效绑定。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Project Directory",
        allowedDependencies = {
                "shared", "jooq", "identity::api", "authorization::api",
                "reliability::api", "audit::api"
        }
)
package com.serviceos.project;
