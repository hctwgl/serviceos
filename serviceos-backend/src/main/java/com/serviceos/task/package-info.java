/**
 * 任务执行模块：拥有人工/自动任务状态机、执行尝试、责任事实、重试时钟与人工接管状态。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Task Execution",
        allowedDependencies = {
                "shared", "identity::api", "authorization::api",
                "audit::api", "reliability::api", "reliability::spi"
        }
)
package com.serviceos.task;
