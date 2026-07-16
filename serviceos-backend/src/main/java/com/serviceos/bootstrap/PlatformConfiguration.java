package com.serviceos.bootstrap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
class PlatformConfiguration {
    @Bean
    Clock systemClock() {
        // PostgreSQL timestamptz 仅保留微秒。统一截断可避免 Outbox 信封 occurredAt
        // （经 JDBC 往返）与 JSON 载荷中的业务时间因纳秒残留而不一致，导致时间线/
        // 恢复处理器失败关闭。
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
