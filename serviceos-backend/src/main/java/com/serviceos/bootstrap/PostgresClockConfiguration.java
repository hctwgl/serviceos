package com.serviceos.bootstrap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Duration;

/**
 * 为所有进入 PostgreSQL 事务的业务时间提供可无损持久化的系统时钟。
 *
 * <p>PostgreSQL {@code timestamp/timestamptz} 只保存微秒精度。若命令首次返回 JDK 系统时钟的
 * 纳秒值，而幂等重放从数据库读取微秒值，同一个已冻结结果会出现时间字段不相等。这里在产生业务
 * 事实前统一到微秒刻度，使首次响应、Outbox、审计、幂等摘要和数据库重放使用完全相同的时间。</p>
 *
 * <p>{@link Primary} 只负责选择生产系统时钟；显式传入构造器的固定测试时钟不经过该 Bean。</p>
 */
@Configuration(proxyBeanMethods = false)
public class PostgresClockConfiguration {
    static final Duration POSTGRES_TIMESTAMP_TICK = Duration.ofNanos(1_000);

    @Bean
    @Primary
    Clock postgresClock() {
        return Clock.tick(Clock.systemUTC(), POSTGRES_TIMESTAMP_TICK);
    }
}
