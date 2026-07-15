package com.serviceos.task.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.task.api.TaskDetail;
import com.serviceos.task.api.TaskExecutionAttemptPage;
import com.serviceos.task.api.TaskExecutionAttemptQueryService;
import com.serviceos.task.api.TaskExecutionAttemptView;
import com.serviceos.task.api.TaskDirectoryQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
final class DefaultTaskExecutionAttemptQueryService implements TaskExecutionAttemptQueryService {
    private final TaskDirectoryQueryService tasks;
    private final TaskDirectoryQueryRepository queries;
    private final Clock clock;

    DefaultTaskExecutionAttemptQueryService(
            TaskDirectoryQueryService tasks,
            TaskDirectoryQueryRepository queries,
            Clock clock
    ) {
        this.tasks = tasks;
        this.queries = queries;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public TaskExecutionAttemptPage list(
            CurrentPrincipal principal,
            String correlationId,
            UUID taskId,
            String cursor,
            int limit
    ) {
        Objects.requireNonNull(taskId, "taskId must not be null");
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }

        // 每一页都先复用 M70 的 tenant 隔离与实时 task.read 鉴权；游标本身从不授予访问权。
        TaskDetail task = tasks.get(principal, correlationId, taskId);
        Integer beforeAttemptNo = decodeCursor(cursor, taskId);
        List<TaskExecutionAttemptView> fetched = queries.findExecutionAttempts(
                principal.tenantId(), taskId, beforeAttemptNo, limit + 1);
        boolean hasMore = fetched.size() > limit;
        List<TaskExecutionAttemptView> selected = hasMore ? fetched.subList(0, limit) : fetched;
        String nextCursor = hasMore
                ? encodeCursor(taskId, selected.getLast().attemptNo())
                : null;
        Instant asOf = clock.instant();
        return new TaskExecutionAttemptPage(task.task().version(), selected, nextCursor, asOf);
    }

    private static String encodeCursor(UUID taskId, int attemptNo) {
        String value = taskId + "|" + attemptNo;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Integer decodeCursor(String cursor, UUID taskId) {
        if (cursor == null) {
            return null;
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 2 || !taskId.toString().equals(parts[0])) {
                throw new IllegalArgumentException();
            }
            int attemptNo = Integer.parseInt(parts[1]);
            if (attemptNo < 1) {
                throw new IllegalArgumentException();
            }
            return attemptNo;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "cursor is invalid for the requested task execution history", exception);
        }
    }
}
