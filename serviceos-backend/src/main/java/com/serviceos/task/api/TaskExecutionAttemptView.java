package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

/** 自动任务单次执行 Attempt 的安全只读投影，不包含 worker 身份、payload 或错误正文。 */
public record TaskExecutionAttemptView(
        UUID attemptId,
        int attemptNo,
        String resultCode,
        String errorCode,
        String resultRef,
        Instant nextRetryAt,
        Instant startedAt,
        Instant finishedAt
) {
}
