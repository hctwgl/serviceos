/**
 * ADR-091 jOOQ 生成物宿主模块：只承载由 Flyway 迁移基线生成的 PostgreSQL Schema 类型
 * （{@code com.serviceos.jooq.generated}），不包含任何业务逻辑。
 *
 * <p>生成物是纯 Schema 镜像、没有需要隐藏的内部实现，因此与 {@code shared} 一样声明为
 * {@code OPEN}，各业务模块的 infrastructure 适配器可直接引用。"生成类型不得泄漏进 Domain
 * 层或跨模块公开 API"（ADR-091 §7）属于模块内部使用纪律，由评审与验收约束。</p>
 *
 * <p>生成物由 {@code scripts/generate-jooq.sh} 产出并提交进 git，禁止手改；与 Flyway 基线的
 * 一致性由 {@code scripts/check-jooq-generated.sh} 门禁保证。</p>
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "jOOQ Generated Schema",
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        allowedDependencies = "shared"
)
package com.serviceos.jooq;
