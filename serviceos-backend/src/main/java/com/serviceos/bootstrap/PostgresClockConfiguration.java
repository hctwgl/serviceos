package com.serviceos.bootstrap;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * 让生产 {@code systemClock} 只产生 PostgreSQL 可无损保存的微秒时间。
 *
 * <p>PostgreSQL {@code timestamp/timestamptz} 只保存微秒精度。若命令首次返回 JDK 系统时钟的
 * 纳秒值，而幂等重放从数据库读取微秒值，同一个已冻结结果会出现时间字段不相等，并可能进一步造成
 * 摘要或恢复身份冲突。</p>
 *
 * <p>这里不注册第二个 {@link Clock}，而是在 Spring 完成既有 {@code systemClock} 初始化时原位包装。
 * 因此生产注入仍只有原来的 Bean，测试中显式声明的 {@code mutableClock} 或构造器固定时钟也不会被
 * 替换。</p>
 */
@Configuration(proxyBeanMethods = false)
public class PostgresClockConfiguration {
    static final Duration POSTGRES_TIMESTAMP_TICK = Duration.ofNanos(1_000);

    @Bean
    static BeanPostProcessor postgresClockPrecisionPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if ("systemClock".equals(beanName) && bean instanceof Clock clock) {
                    return postgresSafeClock(clock);
                }
                return bean;
            }
        };
    }

    static Clock postgresSafeClock(Clock source) {
        return Clock.tick(source, POSTGRES_TIMESTAMP_TICK);
    }
}
