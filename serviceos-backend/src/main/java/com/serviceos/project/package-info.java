/**
 * 项目目录模块：拥有 Client、Brand、Project、ServiceProduct、项目团队及区域分工。
 * 项目模块实现工单模块定义的岗位人员解析 SPI，但不得访问工单内部实现或持久化表。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Project Directory",
        allowedDependencies = {
                "shared", "identity::api", "authorization::api",
                "reliability::api", "audit::api", "configuration::api", "workorder::api"
        }
)
package com.serviceos.project;
