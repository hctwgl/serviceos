package com.serviceos.shared.infrastructure.jooq;

import org.jooq.JSONB;
import org.jooq.impl.AbstractConverter;

/**
 * ADR-091 公共类型绑定：PostgreSQL {@code jsonb} 列在 jOOQ 生成物中统一映射为
 * {@link String}（JSON 文本），与迁移前 MyBatis/JdbcClient 代码中
 * "String + CAST" 的读写语义保持一致。
 *
 * <p>数据库侧仍由 {@link JSONB} 绑定（驱动以 binary transfer 写入，保留 jsonb 的类型检查），
 * 应用侧拿到的是未经解析的 JSON 文本；解析与 Schema 校验仍是各模块 Application 层的职责，
 * 不在数据访问层隐式展开。</p>
 *
 * <p>本 Converter 由 {@code com.serviceos.codegen.JooqCodegen} 通过全局 forcedType 引用，
 * 禁止各模块为 jsonb 自行实现第二套绑定（ADR-091 §3.3）。</p>
 */
public class JsonbStringConverter extends AbstractConverter<JSONB, String> {
    public JsonbStringConverter() {
        super(JSONB.class, String.class);
    }

    @Override
    public String from(JSONB databaseObject) {
        return databaseObject == null ? null : databaseObject.data();
    }

    @Override
    public JSONB to(String userObject) {
        return userObject == null ? null : JSONB.valueOf(userObject);
    }
}
