package com.serviceos.shared.infrastructure;

import org.springframework.jdbc.core.SqlParameterValue;

import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * PostgreSQL JDBC 参数适配器。
 *
 * <p>PostgreSQL JDBC 42.7 不会为 {@link Instant} 自动推断 SQL 类型。所有写入或比较
 * {@code timestamptz} 的参数都必须先转为驱动原生支持的 {@code OffsetDateTime}，并显式声明
 * {@link Types#TIMESTAMP_WITH_TIMEZONE}。统一入口可以避免各模块自行选择时区或遗漏空值类型。</p>
 */
public final class PostgresJdbcParameters {
    private PostgresJdbcParameters() {
    }

    public static SqlParameterValue timestamptz(Instant value) {
        return new SqlParameterValue(
                Types.TIMESTAMP_WITH_TIMEZONE,
                value == null ? null : value.atOffset(ZoneOffset.UTC));
    }
}
