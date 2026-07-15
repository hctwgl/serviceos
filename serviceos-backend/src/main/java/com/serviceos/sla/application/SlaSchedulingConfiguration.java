package com.serviceos.sla.application;

import com.serviceos.sla.api.SlaClockService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 生产部署显式开启后，周期对账已过 TARGET_DUE 且尚未完成的 SLA。 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "serviceos.sla.scheduling-enabled", havingValue = "true")
class SlaSchedulingConfiguration {
    @Bean
    ScheduledSlaScanner scheduledSlaScanner(
            SlaClockService clocks,
            @Value("${serviceos.sla.batch-size:100}") int batchSize
    ) {
        return new ScheduledSlaScanner(clocks, batchSize);
    }

    static final class ScheduledSlaScanner {
        private final SlaClockService clocks;
        private final int batchSize;

        ScheduledSlaScanner(SlaClockService clocks, int batchSize) {
            this.clocks = clocks;
            if (batchSize < 1) {
                throw new IllegalArgumentException("serviceos.sla.batch-size must be positive");
            }
            this.batchSize = batchSize;
        }

        @Scheduled(fixedDelayString = "${serviceos.sla.poll-delay:PT30S}")
        void poll() {
            for (int index = 0; index < batchSize; index++) {
                if (!clocks.detectNextBreach()) {
                    return;
                }
            }
        }
    }
}
