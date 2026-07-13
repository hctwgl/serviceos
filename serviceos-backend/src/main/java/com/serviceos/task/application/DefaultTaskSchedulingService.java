package com.serviceos.task.application;

import com.serviceos.task.api.ScheduleAutomatedTaskCommand;
import com.serviceos.task.api.CreateHandlingTaskCommand;
import com.serviceos.task.api.ScheduledTaskView;
import com.serviceos.task.api.TaskSchedulingService;
import com.serviceos.task.api.CreateWorkflowTaskCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
final class DefaultTaskSchedulingService implements TaskSchedulingService {
    private final TaskSchedulingStore store;

    DefaultTaskSchedulingService(TaskSchedulingStore store) {
        this.store = store;
    }

    @Override
    @Transactional
    public ScheduledTaskView schedule(ScheduleAutomatedTaskCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireText(command.tenantId(), "tenantId");
        requireText(command.taskType(), "taskType");
        requireText(command.businessKey(), "businessKey");
        requireText(command.payloadDigest(), "payloadDigest");
        requireText(command.correlationId(), "correlationId");
        if (!isSha256(command.payloadDigest())) {
            throw new IllegalArgumentException("payloadDigest must be a SHA-256 hex digest");
        }
        if (command.priority() < 0 || command.priority() > 1000) {
            throw new IllegalArgumentException("priority must be between 0 and 1000");
        }
        if (command.maxAttempts() < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (command.nextRunAt() == null) {
            throw new IllegalArgumentException("nextRunAt must not be null");
        }
        // 允许调度立即到期的任务；过旧时间仍由 worker 正常认领，不在这里偷偷改写业务时钟。
        return store.schedule(command);
    }

    @Override
    @Transactional
    public ScheduledTaskView createHandlingTask(CreateHandlingTaskCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireText(command.tenantId(), "tenantId");
        requireText(command.taskType(), "taskType");
        requireText(command.businessKey(), "businessKey");
        requireText(command.payloadDigest(), "payloadDigest");
        requireText(command.correlationId(), "correlationId");
        if (!isSha256(command.payloadDigest())) {
            throw new IllegalArgumentException("payloadDigest must be a SHA-256 hex digest");
        }
        if (command.priority() < 0 || command.priority() > 1000) {
            throw new IllegalArgumentException("priority must be between 0 and 1000");
        }
        if (command.readyAt() == null) {
            throw new IllegalArgumentException("readyAt must not be null");
        }
        return store.createHandlingTask(command);
    }

    @Override
    @Transactional
    public ScheduledTaskView createWorkflowTask(CreateWorkflowTaskCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireText(command.tenantId(), "tenantId");
        requireText(command.workflowNodeId(), "workflowNodeId");
        requireText(command.taskType(), "taskType");
        requireText(command.payloadDigest(), "payloadDigest");
        requireText(command.workflowDefinitionDigest(), "workflowDefinitionDigest");
        requireText(command.correlationId(), "correlationId");
        requireText(command.causationId(), "causationId");
        Objects.requireNonNull(command.projectId(), "projectId must not be null");
        Objects.requireNonNull(command.workOrderId(), "workOrderId must not be null");
        Objects.requireNonNull(command.workflowInstanceId(), "workflowInstanceId must not be null");
        Objects.requireNonNull(command.stageInstanceId(), "stageInstanceId must not be null");
        Objects.requireNonNull(command.workflowNodeInstanceId(), "workflowNodeInstanceId must not be null");
        Objects.requireNonNull(command.workflowDefinitionVersionId(), "workflowDefinitionVersionId must not be null");
        Objects.requireNonNull(command.taskKind(), "taskKind must not be null");
        Objects.requireNonNull(command.readyAt(), "readyAt must not be null");
        if (!isSha256(command.payloadDigest()) || !isSha256(command.workflowDefinitionDigest())) {
            throw new IllegalArgumentException("workflow task digests must be SHA-256 hex digests");
        }
        if (command.priority() < 0 || command.priority() > 1000 || command.maxAttempts() < 1) {
            throw new IllegalArgumentException("invalid workflow task priority or maxAttempts");
        }
        return store.createWorkflowTask(command);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }
}
