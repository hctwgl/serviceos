/**
 * 审计模块：保存关键命令的主体、目标、结果与关联链路，不保存敏感业务正文。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Audit",
        allowedDependencies = {"shared", "jooq"}
)
package com.serviceos.audit;
