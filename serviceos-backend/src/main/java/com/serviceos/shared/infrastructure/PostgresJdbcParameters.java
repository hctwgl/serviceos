package com.serviceos.shared.infrastructure;

import org.springframework.jdbc.core.SqlParameterValue;

import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * PostgreSQL JDBC 参数适配器。
 *
 * <p>PostgreSQL JDBC 42.7 不会为 {@link Instant} 自动推断 SQL 类型。所有写入或比较
 * {@code timestamptz} 的参数都必须先转为驱动原生支持的 {@code OffsetDateTime}，并显式声明
 * {@link Types#TIMESTAMP_WITH_TIMEZONE}。统一入口可以避免各模块自行选择时区或遗漏空值类型。</p>
 *
 * <p>写入前截断到微秒，与 PostgreSQL {@code timestamptz} 存储精度对齐，避免应用侧残留
 * 纳秒在往返后与载荷时间比较失败。</p>
 */
public final class PostgresJdbcParameters {
    private PostgresJdbcParameters() {
    }

    public static SqlParameterValue timestamptz(Instant value) {
        return new SqlParameterValue(
                Types.TIMESTAMP_WITH_TIMEZONE,
                value == null ? null : value.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC));
    }
}
