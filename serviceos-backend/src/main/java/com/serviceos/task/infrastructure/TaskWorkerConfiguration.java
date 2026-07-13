package com.serviceos.task.infrastructure;

import com.serviceos.task.application.TaskExecutionQueue;
import com.serviceos.task.application.TaskExecutionWorker;
import com.serviceos.task.application.TaskHandlerRegistry;
import com.serviceos.task.spi.AutomatedTaskHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 只有部署至少一个真实任务执行器时才创建 worker，避免把任务认领后交给 no-op 处理器。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(AutomatedTaskHandler.class)
class TaskWorkerConfiguration {

    @Bean
    TaskHandlerRegistry taskHandlerRegistry(List<AutomatedTaskHandler> handlers) {
        return new TaskHandlerRegistry(handlers);
    }

    @Bean
    TaskExecutionWorker taskExecutionWorker(
            TaskExecutionQueue queue,
            TaskHandlerRegistry handlers,
            @Value("${serviceos.task.worker-id:}") String configuredWorkerId,
            @Value("${serviceos.task.lease:PT30S}") Duration lease
    ) {
        String workerId = configuredWorkerId == null || configuredWorkerId.isBlank()
                ? "task-worker-" + UUID.randomUUID()
                : configuredWorkerId;
        return new TaskExecutionWorker(queue, handlers, workerId, lease);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "serviceos.task.scheduling-enabled", havingValue = "true")
    static class SchedulingConfiguration {
        @Bean
        ScheduledTaskRunner scheduledTaskRunner(
                TaskExecutionWorker worker,
                @Value("${serviceos.task.batch-size:100}") int batchSize
        ) {
            return new ScheduledTaskRunner(worker, batchSize);
        }
    }

    static final class ScheduledTaskRunner {
        private final TaskExecutionWorker worker;
        private final int batchSize;

        ScheduledTaskRunner(TaskExecutionWorker worker, int batchSize) {
            this.worker = worker;
            this.batchSize = Math.max(1, batchSize);
        }

        @Scheduled(fixedDelayString = "${serviceos.task.poll-delay:PT1S}")
        void poll() {
            for (int index = 0; index < batchSize; index++) {
                if (worker.runOnce() == TaskExecutionWorker.RunResult.EMPTY) {
                    return;
                }
            }
        }
    }
}
