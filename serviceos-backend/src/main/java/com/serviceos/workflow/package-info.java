/**
 * 工作流运行时：消费不可变流程定义，创建流程/阶段实例，并通过公开端口编排工单和任务。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Workflow Runtime",
        allowedDependencies = {
                "shared", "jooq", "reliability::api", "reliability::spi",
                "configuration::api", "workorder::api", "task::api", "identity::api"
        }
)
package com.serviceos.workflow;
