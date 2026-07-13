package com.serviceos.task.application;

import com.serviceos.task.spi.AutomatedTaskHandler;
import com.serviceos.task.spi.TaskExecutionException;
import com.serviceos.task.spi.TaskExecutionResult;

import java.time.Duration;
import java.util.Objects;

/**
 * 自动任务单步 worker：短事务认领，事务外执行，再以短事务保存结果。
 *
 * <p>未分类异常默认进入人工接管，绝不假设可安全重放外部副作用。</p>
 */
public final class TaskExecutionWorker {
    public enum RunResult { EMPTY, SUCCEEDED, RETRY_SCHEDULED, MANUAL_INTERVENTION }

    private static final String MISSING_HANDLER = "TASK_HANDLER_MISSING";
    private static final String UNCLASSIFIED_FAILURE = "UNCLASSIFIED_HANDLER_FAILURE";

    private final TaskExecutionQueue queue;
    private final TaskHandlerRegistry handlers;
    private final String workerId;
    private final Duration leaseDuration;

    public TaskExecutionWorker(
            TaskExecutionQueue queue,
            TaskHandlerRegistry handlers,
            String workerId,
            Duration leaseDuration
    ) {
        this.queue = Objects.requireNonNull(queue);
        this.handlers = Objects.requireNonNull(handlers);
        this.workerId = requireText(workerId, "workerId");
        this.leaseDuration = Objects.requireNonNull(leaseDuration);
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
    }

    public RunResult runOnce() {
        if (queue.recoverOneExhaustedLease(workerId)) {
            return RunResult.MANUAL_INTERVENTION;
        }
        return queue.claimNext(workerId, leaseDuration)
                .map(this::executeClaimed)
                .orElse(RunResult.EMPTY);
    }

    private RunResult executeClaimed(ClaimedTask task) {
        AutomatedTaskHandler handler = handlers.find(task.taskType()).orElse(null);
        TaskExecutionOutcome outcome;
        if (handler == null) {
            outcome = TaskExecutionOutcome.finalFailure(MISSING_HANDLER);
        } else {
            outcome = invoke(handler, task);
        }
        TaskResolution resolution = queue.resolve(task, workerId, outcome);
        return switch (resolution.status()) {
            case SUCCEEDED -> RunResult.SUCCEEDED;
            case RETRY_SCHEDULED -> RunResult.RETRY_SCHEDULED;
            case MANUAL_INTERVENTION -> RunResult.MANUAL_INTERVENTION;
        };
    }

    private static TaskExecutionOutcome invoke(AutomatedTaskHandler handler, ClaimedTask task) {
        try {
            TaskExecutionResult result = Objects.requireNonNull(
                    handler.execute(task.toContext()), "Task handler result must not be null");
            return TaskExecutionOutcome.success(result.resultRef());
        } catch (TaskExecutionException exception) {
            return switch (exception.kind()) {
                case RETRYABLE -> TaskExecutionOutcome.retry(exception.errorCode(), exception.retryAt());
                case FINAL -> TaskExecutionOutcome.finalFailure(exception.errorCode());
                case UNKNOWN -> TaskExecutionOutcome.unknown(exception.errorCode());
            };
        } catch (Exception exception) {
            // 未经执行器显式分类的异常可能发生在外部已接收请求之后，默认转人工，禁止盲重试。
            return TaskExecutionOutcome.finalFailure(UNCLASSIFIED_FAILURE);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
