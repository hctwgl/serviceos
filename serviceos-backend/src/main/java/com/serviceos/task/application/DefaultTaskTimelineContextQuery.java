package com.serviceos.task.application;

import com.serviceos.task.api.TaskTimelineContext;
import com.serviceos.task.api.TaskTimelineContextQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
final class DefaultTaskTimelineContextQuery implements TaskTimelineContextQuery {
    private final TaskDirectoryQueryRepository queries;

    DefaultTaskTimelineContextQuery(TaskDirectoryQueryRepository queries) {
        this.queries = queries;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaskTimelineContext> find(String tenantId, UUID taskId) {
        return queries.findTimelineContext(tenantId, taskId);
    }
}
