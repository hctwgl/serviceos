package com.serviceos.project.infrastructure;

import com.serviceos.project.application.ProjectQueryRepository;
import com.serviceos.project.application.ProjectScopeRevision;
import com.serviceos.project.domain.Project;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
final class MyBatisProjectQueryRepository implements ProjectQueryRepository {
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final ProjectQueryMapper mapper;

    MyBatisProjectQueryRepository(ProjectQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Project> findPage(
            String tenantId, boolean tenantWide, List<UUID> projectIds, String clientId, String status,
            LocalDate activeOn, String cursorCode, UUID cursorId, int fetchSize
    ) {
        return mapper.findPage(tenantId, tenantWide, projectIds.stream().map(UUID::toString).toList(),
                clientId, status, activeOn, cursorCode, cursorId, fetchSize).stream()
                .map(MyBatisProjectQueryRepository::project).toList();
    }

    @Override
    public List<ProjectScopeRevision> findScopeRevisionPage(
            String tenantId, UUID projectId, Long cursorVersion, int fetchSize
    ) {
        return mapper.findScopeRevisionPage(tenantId, projectId, cursorVersion, fetchSize).stream()
                .map(MyBatisProjectQueryRepository::revision).toList();
    }

    private static Project project(Map<String, Object> row) {
        return new Project(uuid(row, "projectId"), string(row, "tenantId"), string(row, "projectCode"),
                string(row, "clientId"), string(row, "projectName"), localDate(row, "startsOn"),
                localDate(row, "endsOn"), jsonList(row, "regionCodes"), jsonList(row, "networkIds"),
                Project.Status.valueOf(string(row, "projectStatus")), number(row, "aggregateVersion").longValue(),
                instant(row, "createdAt"));
    }

    private static ProjectScopeRevision revision(Map<String, Object> row) {
        return new ProjectScopeRevision(uuid(row, "revisionId"), string(row, "tenantId"),
                uuid(row, "projectId"), number(row, "expectedVersion").longValue(),
                number(row, "aggregateVersion").longValue(), jsonList(row, "regionCodes"),
                jsonList(row, "networkIds"), jsonList(row, "addedRegionCodes"),
                jsonList(row, "removedRegionCodes"), jsonList(row, "addedNetworkIds"),
                jsonList(row, "removedNetworkIds"), string(row, "reason"), string(row, "revisedBy"),
                instant(row, "revisedAt"));
    }

    private static String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static UUID uuid(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    private static Number number(Map<String, Object> row, String key) {
        return (Number) row.get(key);
    }

    private static LocalDate localDate(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return null;
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        return LocalDate.parse(value.toString());
    }

    private static Instant instant(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        return Instant.parse(value.toString());
    }

    private static List<String> jsonList(Map<String, Object> row, String key) {
        try {
            return List.of(JSON.readValue(string(row, key), String[].class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("项目查询结果包含非法 JSON 数组: " + key, exception);
        }
    }
}
