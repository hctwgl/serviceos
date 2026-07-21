/**
 * 安全文件模块：拥有上传会话、不可变文件对象、扫描结果和短期访问授权。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Secure Files",
        allowedDependencies = {
                "shared", "jooq", "identity::api", "authorization::api", "audit::api",
                "reliability::api", "task::api", "task::spi"
        }
)
package com.serviceos.files;
