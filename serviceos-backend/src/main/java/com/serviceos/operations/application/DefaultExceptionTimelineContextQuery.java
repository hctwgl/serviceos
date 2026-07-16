package com.serviceos.operations.application;

import com.serviceos.operations.api.ExceptionTimelineContext;
import com.serviceos.operations.api.ExceptionTimelineContextQuery;
import com.serviceos.operations.api.OperationalExceptionItem;
import com.serviceos.task.api.TaskTimelineContext;
import com.serviceos.task.api.TaskTimelineContextQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 通过工作台权威行与 Task 公开端口解析异常所属工单；不加载备注或错误正文。
 */
@Service
final class DefaultExceptionTimelineContextQuery implements ExceptionTimelineContextQuery {
    private final OperationalExceptionWorkbenchRepository exceptions;
    private final TaskTimelineContextQuery tasks;

    DefaultExceptionTimelineContextQuery(
            OperationalExceptionWorkbenchRepository exceptions,
            TaskTimelineContextQuery tasks
    ) {
        this.exceptions = exceptions;
        this.tasks = tasks;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExceptionTimelineContext> find(String tenantId, UUID exceptionId) {
        return exceptions.findById(tenantId, exceptionId).map(item -> resolve(tenantId, item));
    }

    private ExceptionTimelineContext resolve(String tenantId, OperationalExceptionItem item) {
        UUID taskId = resolveSourceTaskId(item);
        if (taskId == null) {
            // 无 Task 链接的异常不得猜测工单归属；投影方应忽略。
            return new ExceptionTimelineContext(item.exceptionId(), null, null);
        }
        TaskTimelineContext task = tasks.find(tenantId, taskId)
                .orElseThrow(() -> new IllegalStateException("时间线事件引用的 Task 不存在"));
        return new ExceptionTimelineContext(item.exceptionId(), task.projectId(), task.workOrderId());
    }

    private static UUID resolveSourceTaskId(OperationalExceptionItem item) {
        if (item.taskId() != null) {
            return item.taskId();
        }
        if (!"TASK".equals(item.sourceType()) || item.sourceId() == null || item.sourceId().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(item.sourceId());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
