/**
 * 运营异常模块：把自动执行最终失败转成可分配、可处理、可审计的人工闭环。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Operational Exception",
        allowedDependencies = {"shared", "reliability::api", "reliability::spi", "task::api"}
)
package com.serviceos.operations;
