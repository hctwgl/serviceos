/**
 * 任务执行模块：拥有自动任务、执行尝试、重试时钟与人工接管状态。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Task Execution",
        allowedDependencies = {"shared", "reliability::api"}
)
package com.serviceos.task;
