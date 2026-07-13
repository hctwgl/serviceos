package com.serviceos.reliability.infrastructure;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Outbox 积压量与最老消息年龄。Gauge 查询不包含 tenant/event 等无界标签。
 */
@Component
final class OutboxBacklogMetrics implements MeterBinder {
    private final JdbcClient jdbc;

    OutboxBacklogMetrics(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("serviceos.outbox.backlog", this, OutboxBacklogMetrics::backlog)
                .description("Publishable or currently claimed outbox messages")
                .tag("module", "reliability")
                .register(registry);
        Gauge.builder("serviceos.outbox.oldest.age", this, OutboxBacklogMetrics::oldestAgeSeconds)
                .description("Age of the oldest publishable outbox message")
                .baseUnit("seconds")
                .tag("module", "reliability")
                .register(registry);
    }

    private double backlog() {
        return queryNumber("""
                SELECT count(*)::double precision
                  FROM rel_outbox_event
                 WHERE status IN ('PENDING', 'FAILED', 'CLAIMED')
                """);
    }

    private double oldestAgeSeconds() {
        return queryNumber("""
                SELECT COALESCE(
                    GREATEST(EXTRACT(EPOCH FROM (clock_timestamp() - MIN(available_at))), 0),
                    0
                )::double precision
                  FROM rel_outbox_event
                 WHERE status IN ('PENDING', 'FAILED', 'CLAIMED')
                """);
    }

    private double queryNumber(String sql) {
        try {
            return jdbc.sql(sql).query(Double.class).single();
        } catch (DataAccessException exception) {
            // 抓取期间数据库不可用时返回 NaN，让采集端识别缺失；不得伪造为“无积压”。
            return Double.NaN;
        }
    }
}
