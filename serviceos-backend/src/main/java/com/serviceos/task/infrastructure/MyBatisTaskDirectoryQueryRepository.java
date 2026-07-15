package com.serviceos.task.infrastructure;

import com.serviceos.task.api.InputVersionRef;
import com.serviceos.task.api.TaskDetail;
import com.serviceos.task.api.TaskDirectoryItem;
import com.serviceos.task.api.TaskExecutionAttemptView;
import com.serviceos.task.application.TaskDirectoryQueryRepository;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
final class MyBatisTaskDirectoryQueryRepository implements TaskDirectoryQueryRepository {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final TaskDirectoryQueryMapper mapper;

    MyBatisTaskDirectoryQueryRepository(TaskDirectoryQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<TaskDirectoryItem> findPage(
            String tenantId,
            boolean tenantWide,
            List<UUID> projectIds,
            UUID projectId,
            String taskKind,
            String status,
            String assigneeId,
            Integer cursorPriority,
            Instant cursorNextRunAt,
            Instant cursorCreatedAt,
            UUID cursorId,
            int fetchSize
    ) {
        return mapper.findPage(
                        tenantId,
                        tenantWide,
                        projectIds.stream().map(UUID::toString).toList(),
                        projectId,
                        taskKind,
                        status,
                        assigneeId,
                        cursorPriority,
                        cursorNextRunAt,
                        cursorCreatedAt,
                        cursorId,
                        fetchSize)
                .stream()
                .map(MyBatisTaskDirectoryQueryRepository::item)
                .toList();
    }

    @Override
    public Optional<TaskDetail> findDetail(String tenantId, UUID taskId, Instant asOf) {
        return Optional.ofNullable(mapper.findDetail(tenantId, taskId))
                .map(row -> detail(row, asOf));
    }

    @Override
    public Optional<AllowedActionState> findAllowedActionState(
            String tenantId, UUID taskId, String principalId) {
        return Optional.ofNullable(mapper.findAllowedActionState(tenantId, taskId, principalId))
                .map(row -> new AllowedActionState(
                        string(row, "taskKind"),
                        string(row, "status"),
                        number(row, "version").longValue(),
                        string(row, "claimedBy"),
                        uuid(row, "workflowNodeInstanceId"),
                        bool(row, "actorCandidate"),
                        bool(row, "actorResponsible"),
                        bool(row, "activeGuard")));
    }

    @Override
    public List<TaskExecutionAttemptView> findExecutionAttempts(
            String tenantId,
            UUID taskId,
            Integer beforeAttemptNo,
            int fetchSize
    ) {
        return mapper.findExecutionAttempts(tenantId, taskId, beforeAttemptNo, fetchSize)
                .stream()
                .map(row -> new TaskExecutionAttemptView(
                        uuid(row, "attemptId"),
                        number(row, "attemptNo").intValue(),
                        string(row, "resultCode"),
                        string(row, "errorCode"),
                        string(row, "resultRef"),
                        instant(row, "nextRetryAt"),
                        instant(row, "startedAt"),
                        instant(row, "finishedAt")))
                .toList();
    }

    private static TaskDirectoryItem item(Map<String, Object> row) {
        return new TaskDirectoryItem(
                uuid(row, "id"),
                uuid(row, "projectId"),
                uuid(row, "workOrderId"),
                string(row, "taskType"),
                string(row, "taskKind"),
                string(row, "stageCode"),
                number(row, "priority").intValue(),
                string(row, "status"),
                instant(row, "nextRunAt"),
                string(row, "claimedBy"),
                number(row, "attemptCount").intValue(),
                number(row, "maxAttempts").intValue(),
                number(row, "version").longValue(),
                instant(row, "createdAt"),
                instant(row, "updatedAt"));
    }

    private static TaskDetail detail(Map<String, Object> row, Instant asOf) {
        return new TaskDetail(
                item(row),
                uuid(row, "workflowInstanceId"),
                uuid(row, "stageInstanceId"),
                uuid(row, "workflowNodeInstanceId"),
                string(row, "workflowNodeId"),
                uuid(row, "workflowDefinitionVersionId"),
                string(row, "workflowDefinitionDigest"),
                uuid(row, "configurationBundleId"),
                string(row, "configurationBundleDigest"),
                string(row, "formRef"),
                string(row, "responsibleUserId"),
                strings(row, "candidateUserIds"),
                instant(row, "claimedAt"),
                instant(row, "startedAt"),
                instant(row, "completedAt"),
                string(row, "resultRef"),
                string(row, "resultDigest"),
                inputVersionRefs(row, "inputVersionRefs"),
                asOf);
    }

    private static List<String> strings(Map<String, Object> row, String key) {
        try {
            return List.of(JSON.readValue(string(row, key), String[].class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("任务详情包含非法候选人 JSON", exception);
        }
    }

    private static List<InputVersionRef> inputVersionRefs(Map<String, Object> row, String key) {
        try {
            return List.of(JSON.readValue(string(row, key), InputVersionRef[].class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("任务详情包含非法输入版本 JSON", exception);
        }
    }

    private static String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static UUID uuid(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return null;
        }
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private static Number number(Map<String, Object> row, String key) {
        return (Number) row.get(key);
    }

    private static Instant instant(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return Instant.parse(value.toString());
    }

    private static boolean bool(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }
}
