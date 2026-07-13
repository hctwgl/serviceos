package com.serviceos.task.application;

import com.serviceos.task.spi.TaskExecutionContext;

import java.util.UUID;

public record ClaimedTask(
        UUID taskId,
        UUID attemptId,
        String tenantId,
        String taskType,
        String businessKey,
        String payloadRef,
        String payloadDigest,
        String correlationId,
        int attemptNo,
        int maxAttempts,
        long taskVersion
) {
    TaskExecutionContext toContext() {
        return new TaskExecutionContext(
                taskId, attemptId, tenantId, taskType, businessKey,
                payloadRef, payloadDigest, correlationId, attemptNo);
    }
}
