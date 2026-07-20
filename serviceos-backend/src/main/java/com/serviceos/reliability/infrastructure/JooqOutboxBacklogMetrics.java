package com.serviceos.reliability.infrastructure;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import static com.serviceos.jooq.generated.tables.RelOutboxEvent.REL_OUTBOX_EVENT;

/**
 * Outbox 积压量与最老消息年龄。Gauge 查询不包含 tenant/event 等无界标签。
 */
@Component
final class JooqOutboxBacklogMetrics implements MeterBinder {
    private static final Condition ACTIVE =
            REL_OUTBOX_EVENT.STATUS.in("PENDING", "FAILED", "CLAIMED");

    // 数值语义与原 SQL 完全一致（EXTRACT epoch / GREATEST 下限 0 / COALESCE 空集 0），
    // 仅把 MIN(available_at) 换成生成列引用，使列重命名在编译期暴露。
    private static final Field<Double> OLDEST_AGE_SECONDS = DSL.field(
            "COALESCE(GREATEST(EXTRACT(EPOCH FROM (clock_timestamp() - {0})), 0), 0)::double precision",
            Double.class,
            DSL.min(REL_OUTBOX_EVENT.AVAILABLE_AT));

    private final DSLContext dsl;

    JooqOutboxBacklogMetrics(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("serviceos.outbox.backlog", this, JooqOutboxBacklogMetrics::backlog)
                .description("Publishable or currently claimed outbox messages")
                .tag("module", "reliability")
                .register(registry);
        Gauge.builder("serviceos.outbox.oldest.age", this, JooqOutboxBacklogMetrics::oldestAgeSeconds)
                .description("Age of the oldest publishable outbox message")
                .baseUnit("seconds")
                .tag("module", "reliability")
                .register(registry);
    }

    private double backlog() {
        try {
            return dsl.fetchCount(REL_OUTBOX_EVENT, ACTIVE);
        } catch (DataAccessException exception) {
            // 抓取期间数据库不可用时返回 NaN，让采集端识别缺失；不得伪造为“无积压”。
            return Double.NaN;
        }
    }

    private double oldestAgeSeconds() {
        try {
            // 聚合查询恒返回一行；表达式已保证非 NULL，防御性兜底仍按缺失处理。
            Double value = dsl.select(OLDEST_AGE_SECONDS)
                    .from(REL_OUTBOX_EVENT)
                    .where(ACTIVE)
                    .fetchOne(OLDEST_AGE_SECONDS);
            return value == null ? Double.NaN : value;
        } catch (DataAccessException exception) {
            // 抓取期间数据库不可用时返回 NaN，让采集端识别缺失；不得伪造为“无积压”。
            return Double.NaN;
        }
    }
}
