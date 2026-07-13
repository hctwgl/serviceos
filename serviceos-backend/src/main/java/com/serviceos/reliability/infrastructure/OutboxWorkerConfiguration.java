package com.serviceos.reliability.infrastructure;

import com.serviceos.reliability.application.OutboxQueue;
import com.serviceos.reliability.application.OutboxWorker;
import com.serviceos.reliability.spi.OutboxPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * 只有部署了真实 OutboxPublisher 时才创建 worker；禁止用日志/no-op 适配器伪造发布成功。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(OutboxPublisher.class)
class OutboxWorkerConfiguration {

    @Bean
    OutboxWorker outboxWorker(
            OutboxQueue queue,
            OutboxPublisher publisher,
            Clock clock,
            @Value("${serviceos.outbox.worker-id:}") String configuredWorkerId,
            @Value("${serviceos.outbox.lease:PT30S}") Duration lease,
            @Value("${serviceos.outbox.max-attempts:8}") int maxAttempts
    ) {
        String workerId = configuredWorkerId == null || configuredWorkerId.isBlank()
                ? "worker-" + UUID.randomUUID()
                : configuredWorkerId;
        return new OutboxWorker(queue, publisher, clock, workerId, lease, maxAttempts);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "serviceos.outbox.scheduling-enabled", havingValue = "true")
    static class SchedulingConfiguration {
        @Bean
        ScheduledOutboxRunner scheduledOutboxRunner(
                OutboxWorker worker,
                @Value("${serviceos.outbox.batch-size:100}") int batchSize
        ) {
            return new ScheduledOutboxRunner(worker, batchSize);
        }
    }

    static final class ScheduledOutboxRunner {
        private final OutboxWorker worker;
        private final int batchSize;

        ScheduledOutboxRunner(OutboxWorker worker, int batchSize) {
            this.worker = worker;
            this.batchSize = Math.max(1, batchSize);
        }

        @Scheduled(fixedDelayString = "${serviceos.outbox.poll-delay:PT1S}")
        void poll() {
            for (int index = 0; index < batchSize; index++) {
                if (worker.runOnce() == OutboxWorker.RunResult.EMPTY) {
                    return;
                }
            }
        }
    }
}
