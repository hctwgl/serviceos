package com.serviceos.shared.infrastructure.jooq;

import org.jooq.impl.AbstractConverter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * ADR-091 公共类型绑定：PostgreSQL {@code timestamptz} 列在 jOOQ 生成物中统一映射为
 * {@link Instant}，语义与迁移前 JdbcClient 时代的统一参数适配完全一致——写入前截断到微秒
 * （{@code timestamptz} 的存储精度），并以 UTC 偏移交给驱动，
 * 避免应用侧残留纳秒在往返后与载荷时间比较失败。
 *
 * <p>读取方向用 {@link OffsetDateTime#toInstant()} 取绝对时刻，与驱动返回的偏移无关；
 * 写回时统一归一到 UTC，保证生成 SQL 中的字面量与时区设置解耦。</p>
 *
 * <p>本 Converter 由 {@code com.serviceos.codegen.JooqCodegen} 通过全局 forcedType 引用，
 * 禁止各模块为 timestamptz 自行实现第二套绑定（ADR-091 §3.3）。</p>
 */
public class TimestamptzInstantConverter extends AbstractConverter<OffsetDateTime, Instant> {
    public TimestamptzInstantConverter() {
        super(OffsetDateTime.class, Instant.class);
    }

    @Override
    public Instant from(OffsetDateTime databaseObject) {
        return databaseObject == null ? null : databaseObject.toInstant().truncatedTo(ChronoUnit.MICROS);
    }

    @Override
    public OffsetDateTime to(Instant userObject) {
        return userObject == null ? null : userObject.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }
}
