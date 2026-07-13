package com.serviceos.task.application;

import com.serviceos.task.spi.AutomatedTaskHandler;
import com.serviceos.task.spi.TaskExecutionContext;
import com.serviceos.task.spi.TaskExecutionException;
import com.serviceos.task.spi.TaskExecutionResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskExecutionWorkerTest {
    private final FakeQueue queue = new FakeQueue();

    @Test
    void confirmedSuccessIsPersistedAsSucceeded() {
        AutomatedTaskHandler handler = handler(context -> TaskExecutionResult.succeeded("result-1"));
        TaskExecutionWorker worker = worker(handler);

        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        assertThat(queue.outcomes).singleElement().satisfies(outcome -> {
            assertThat(outcome.kind()).isEqualTo(TaskExecutionOutcome.Kind.SUCCESS);
            assertThat(outcome.resultRef()).isEqualTo("result-1");
        });
    }

    @Test
    void explicitlyRetryableFailureUsesHandlerProvidedRetryTime() {
        Instant retryAt = Instant.parse("2026-07-13T05:00:00Z");
        AutomatedTaskHandler handler = handler(context -> {
            throw TaskExecutionException.retryable("BROKER_UNAVAILABLE", retryAt, null);
        });
        queue.resolution = new TaskResolution(TaskResolution.Status.RETRY_SCHEDULED);

        assertThat(worker(handler).runOnce()).isEqualTo(TaskExecutionWorker.RunResult.RETRY_SCHEDULED);
        assertThat(queue.outcomes.getFirst().retryAt()).isEqualTo(retryAt);
    }

    @Test
    void unknownExternalResultGoesToManualInterventionWithoutRetry() {
        AutomatedTaskHandler handler = handler(context -> {
            throw TaskExecutionException.unknown("REMOTE_RESULT_UNKNOWN", null);
        });
        queue.resolution = new TaskResolution(TaskResolution.Status.MANUAL_INTERVENTION);

        assertThat(worker(handler).runOnce()).isEqualTo(TaskExecutionWorker.RunResult.MANUAL_INTERVENTION);
        assertThat(queue.outcomes.getFirst().kind()).isEqualTo(TaskExecutionOutcome.Kind.UNKNOWN);
    }

    @Test
    void unclassifiedExceptionFailsClosedInsteadOfBlindRetry() {
        AutomatedTaskHandler handler = handler(context -> {
            throw new IllegalStateException("possibly failed after remote side effect");
        });
        queue.resolution = new TaskResolution(TaskResolution.Status.MANUAL_INTERVENTION);

        worker(handler).runOnce();

        assertThat(queue.outcomes.getFirst()).satisfies(outcome -> {
            assertThat(outcome.kind()).isEqualTo(TaskExecutionOutcome.Kind.FINAL_FAILURE);
            assertThat(outcome.errorCode()).isEqualTo("UNCLASSIFIED_HANDLER_FAILURE");
        });
    }

    @Test
    void missingHandlerIsAnAuditableFinalFailure() {
        TaskExecutionWorker worker = new TaskExecutionWorker(
                queue, new TaskHandlerRegistry(List.of()), "worker-1", Duration.ofSeconds(30));
        queue.resolution = new TaskResolution(TaskResolution.Status.MANUAL_INTERVENTION);

        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.MANUAL_INTERVENTION);
        assertThat(queue.outcomes.getFirst().errorCode()).isEqualTo("TASK_HANDLER_MISSING");
    }

    @Test
    void exhaustedExpiredLeaseIsRecoveredBeforeNewWork() {
        queue.recoverExhausted = true;
        TaskExecutionWorker worker = worker(handler(context -> TaskExecutionResult.succeeded("unused")));

        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.MANUAL_INTERVENTION);
        assertThat(queue.claimCalls).isZero();
    }

    @Test
    void duplicateTaskTypeHandlersFailAtStartup() {
        AutomatedTaskHandler first = handler(context -> TaskExecutionResult.succeeded("a"));
        AutomatedTaskHandler second = handler(context -> TaskExecutionResult.succeeded("b"));

        assertThatThrownBy(() -> new TaskHandlerRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
    }

    private TaskExecutionWorker worker(AutomatedTaskHandler handler) {
        return new TaskExecutionWorker(
                queue, new TaskHandlerRegistry(List.of(handler)), "worker-1", Duration.ofSeconds(30));
    }

    private static AutomatedTaskHandler handler(Executing function) {
        return new AutomatedTaskHandler() {
            @Override
            public String taskType() {
                return "test.task";
            }

            @Override
            public TaskExecutionResult execute(TaskExecutionContext context) throws Exception {
                return function.execute(context);
            }
        };
    }

    @FunctionalInterface
    private interface Executing {
        TaskExecutionResult execute(TaskExecutionContext context) throws Exception;
    }

    private static final class FakeQueue implements TaskExecutionQueue {
        private final ClaimedTask task = new ClaimedTask(
                UUID.randomUUID(), UUID.randomUUID(), "tenant-1", "test.task", "business-1",
                "payload://1", "a".repeat(64), "corr-1", 1, 3, 2);
        private final List<TaskExecutionOutcome> outcomes = new ArrayList<>();
        private TaskResolution resolution = new TaskResolution(TaskResolution.Status.SUCCEEDED);
        private boolean recoverExhausted;
        private int claimCalls;

        @Override
        public boolean recoverOneExhaustedLease(String recoveryWorkerId) {
            return recoverExhausted;
        }

        @Override
        public Optional<ClaimedTask> claimNext(String workerId, Duration leaseDuration) {
            claimCalls++;
            return Optional.of(task);
        }

        @Override
        public TaskResolution resolve(ClaimedTask task, String workerId, TaskExecutionOutcome outcome) {
            outcomes.add(outcome);
            return resolution;
        }
    }
}
