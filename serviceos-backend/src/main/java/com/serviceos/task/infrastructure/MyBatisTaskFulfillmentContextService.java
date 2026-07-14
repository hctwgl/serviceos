package com.serviceos.task.infrastructure;

import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 将 Task 内部表投影为稳定公开上下文，调用方不能据此修改 Task。 */
@Service
final class MyBatisTaskFulfillmentContextService implements TaskFulfillmentContextService {
    private final TaskFulfillmentContextMapper mapper;

    MyBatisTaskFulfillmentContextService(TaskFulfillmentContextMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<TaskFulfillmentContext> find(String tenantId, UUID taskId) {
        return Optional.ofNullable(mapper.find(tenantId, taskId)).map(this::context);
    }

    private TaskFulfillmentContext context(Map<String, Object> row) {
        return new TaskFulfillmentContext(
                uuid(row, "taskId"), uuid(row, "projectId"), uuid(row, "workOrderId"),
                text(row, "taskType"), text(row, "taskKind"), text(row, "formRef"), text(row, "status"),
                text(row, "responsiblePrincipalId"), ((Number) row.get("version")).longValue());
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static UUID uuid(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value instanceof UUID id ? id : UUID.fromString(value.toString());
    }
}
