package com.serviceos.dispatch.application;

import com.serviceos.dispatch.api.ServiceAssignmentTimeoutScanner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 生产部署显式开启后，周期扫描已到期的 ServiceAssignment 激活 saga。 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "serviceos.dispatch.activation-timeout-scheduling-enabled", havingValue = "true")
class ServiceAssignmentTimeoutSchedulingConfiguration {

    @Bean
    ScheduledTimeoutScanner scheduledTimeoutScanner(
            ServiceAssignmentTimeoutScanner scanner,
            @Value("${serviceos.dispatch.activation-timeout-batch-size:100}") int batchSize
    ) {
        return new ScheduledTimeoutScanner(scanner, batchSize);
    }

    static final class ScheduledTimeoutScanner {
        private final ServiceAssignmentTimeoutScanner scanner;
        private final int batchSize;

        ScheduledTimeoutScanner(ServiceAssignmentTimeoutScanner scanner, int batchSize) {
            this.scanner = scanner;
            this.batchSize = Math.max(1, batchSize);
        }

        @Scheduled(fixedDelayString = "${serviceos.dispatch.activation-timeout-poll-delay:PT30S}")
        void poll() {
            for (int index = 0; index < batchSize; index++) {
                if (!scanner.detectNextTimeout()) return;
            }
        }
    }
}
