package com.serviceos.shared;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * PostgreSQL {@code timestamptz} 仅保留微秒。统一截断避免写入/回读后 Instant 相等性失败。
 */
public final class PostgresInstants {
    private PostgresInstants() {
    }

    public static Instant truncate(Instant value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MICROS);
    }
}
