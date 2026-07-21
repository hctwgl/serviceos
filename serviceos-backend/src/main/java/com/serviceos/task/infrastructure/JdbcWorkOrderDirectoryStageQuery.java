package com.serviceos.task.infrastructure;

import com.serviceos.workorder.api.WorkOrderDirectoryAssigneeQuery;
import com.serviceos.workorder.api.WorkOrderDirectoryStageQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * M432/M433/M438/M446：批量解析工单当前阶段码、任务状态、认领主体，以及按阶段/任务状态筛选。
 *
 * <p>使用 PostgreSQL {@code DISTINCT ON}，按 created_at/task_id 取最早 ACTIVE 任务，
 * 与工作区 currentTaskSummary 在列表顺序下的 findFirst 口径一致。</p>
 */
@Component
final class JdbcWorkOrderDirectoryStageQuery
        implements WorkOrderDirectoryStageQuery, WorkOrderDirectoryAssigneeQuery {

    private static final String ACTIVE_STATUSES = """
            'READY', 'PENDING', 'CLAIMED', 'RUNNING',
            'RETRY_WAIT', 'MANUAL_INTERVENTION'
            """;

    private static final String SIDE_CAR_SQL = """
            SELECT DISTINCT ON (work_order_id)
                   work_order_id AS work_order_id,
                   stage_code AS stage_code,
                   status AS task_status,
                   claimed_by AS claimed_by
              FROM tsk_task
             WHERE tenant_id = :tenantId
               AND work_order_id IN (:workOrderIds)
               AND status IN (
            """ + ACTIVE_STATUSES + """
               )
             ORDER BY work_order_id, created_at ASC, task_id ASC
            """;

    private static final String FILTER_STAGE_TENANT_WIDE = """
            SELECT work_order_id
              FROM (
                    SELECT DISTINCT ON (work_order_id)
                           work_order_id AS work_order_id,
                           stage_code AS stage_code
                      FROM tsk_task
                     WHERE tenant_id = :tenantId
                       AND work_order_id IS NOT NULL
                       AND status IN (
            """ + ACTIVE_STATUSES + """
                       )
                     ORDER BY work_order_id, created_at ASC, task_id ASC
                   ) current_tasks
             WHERE stage_code = :stageCode
            """;

    private static final String FILTER_STAGE_PROJECT_SCOPED = """
            SELECT work_order_id
              FROM (
                    SELECT DISTINCT ON (work_order_id)
                           work_order_id AS work_order_id,
                           stage_code AS stage_code
                      FROM tsk_task
                     WHERE tenant_id = :tenantId
                       AND work_order_id IS NOT NULL
                       AND project_id IN (:projectIds)
                       AND status IN (
            """ + ACTIVE_STATUSES + """
                       )
                     ORDER BY work_order_id, created_at ASC, task_id ASC
                   ) current_tasks
             WHERE stage_code = :stageCode
            """;

    private static final String FILTER_TASK_STATUS_TENANT_WIDE = """
            SELECT work_order_id
              FROM (
                    SELECT DISTINCT ON (work_order_id)
                           work_order_id AS work_order_id,
                           status AS task_status
                      FROM tsk_task
                     WHERE tenant_id = :tenantId
                       AND work_order_id IS NOT NULL
                       AND status IN (
            """ + ACTIVE_STATUSES + """
                       )
                     ORDER BY work_order_id, created_at ASC, task_id ASC
                   ) current_tasks
             WHERE task_status = :taskStatus
            """;

    private static final String FILTER_TASK_STATUS_PROJECT_SCOPED = """
            SELECT work_order_id
              FROM (
                    SELECT DISTINCT ON (work_order_id)
                           work_order_id AS work_order_id,
                           status AS task_status
                      FROM tsk_task
                     WHERE tenant_id = :tenantId
                       AND work_order_id IS NOT NULL
                       AND project_id IN (:projectIds)
                       AND status IN (
            """ + ACTIVE_STATUSES + """
                       )
                     ORDER BY work_order_id, created_at ASC, task_id ASC
                   ) current_tasks
             WHERE task_status = :taskStatus
            """;

    private final JdbcClient jdbc;

    JdbcWorkOrderDirectoryStageQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, String> findCurrentStageCodes(String tenantId, Collection<UUID> workOrderIds) {
        Map<UUID, String> stages = new HashMap<>();
        for (var entry : loadCurrentTasks(tenantId, workOrderIds).entrySet()) {
            String stageCode = entry.getValue().stageCode();
            if (stageCode != null && !stageCode.isBlank()) {
                stages.put(entry.getKey(), stageCode.trim());
            }
        }
        return Map.copyOf(stages);
    }

    @Override
    public Map<UUID, String> findCurrentTaskStatuses(String tenantId, Collection<UUID> workOrderIds) {
        Map<UUID, String> statuses = new HashMap<>();
        for (var entry : loadCurrentTasks(tenantId, workOrderIds).entrySet()) {
            String taskStatus = entry.getValue().taskStatus();
            if (taskStatus != null && !taskStatus.isBlank()) {
                statuses.put(entry.getKey(), taskStatus.trim());
            }
        }
        return Map.copyOf(statuses);
    }

    @Override
    public Map<UUID, String> findCurrentClaimedBy(String tenantId, Collection<UUID> workOrderIds) {
        Map<UUID, String> claimed = new HashMap<>();
        for (var entry : loadCurrentTasks(tenantId, workOrderIds).entrySet()) {
            String claimedBy = entry.getValue().claimedBy();
            if (claimedBy != null && !claimedBy.isBlank()) {
                claimed.put(entry.getKey(), claimedBy.trim());
            }
        }
        return Map.copyOf(claimed);
    }

    @Override
    public List<UUID> findWorkOrderIdsByCurrentStageCode(
            String tenantId,
            String stageCode,
            boolean tenantWide,
            Collection<UUID> projectIds
    ) {
        return filterWorkOrderIds(
                tenantId, tenantWide, projectIds,
                tenantWide ? FILTER_STAGE_TENANT_WIDE : FILTER_STAGE_PROJECT_SCOPED,
                "stageCode", stageCode);
    }

    @Override
    public List<UUID> findWorkOrderIdsByCurrentTaskStatus(
            String tenantId,
            String taskStatus,
            boolean tenantWide,
            Collection<UUID> projectIds
    ) {
        return filterWorkOrderIds(
                tenantId, tenantWide, projectIds,
                tenantWide ? FILTER_TASK_STATUS_TENANT_WIDE : FILTER_TASK_STATUS_PROJECT_SCOPED,
                "taskStatus", taskStatus);
    }

    private List<UUID> filterWorkOrderIds(
            String tenantId,
            boolean tenantWide,
            Collection<UUID> projectIds,
            String sql,
            String filterParam,
            String filterValue
    ) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(filterValue, filterParam + " must not be null");
        Objects.requireNonNull(projectIds, "projectIds must not be null");
        if (!tenantWide && projectIds.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>();
        var spec = jdbc.sql(sql).param("tenantId", tenantId).param(filterParam, filterValue);
        if (!tenantWide) {
            spec = spec.param("projectIds", List.copyOf(projectIds));
        }
        spec.query((rs, rowNum) -> {
                    UUID workOrderId = rs.getObject("work_order_id", UUID.class);
                    if (workOrderId != null) {
                        ids.add(workOrderId);
                    }
                    return null;
                })
                .list();
        return List.copyOf(ids);
    }

    private Map<UUID, CurrentTaskRow> loadCurrentTasks(String tenantId, Collection<UUID> workOrderIds) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workOrderIds, "workOrderIds must not be null");
        if (workOrderIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = List.copyOf(workOrderIds);
        Map<UUID, CurrentTaskRow> result = new HashMap<>();
        jdbc.sql(SIDE_CAR_SQL)
                .param("tenantId", tenantId)
                .param("workOrderIds", ids)
                .query((rs, rowNum) -> {
                    UUID workOrderId = rs.getObject("work_order_id", UUID.class);
                    if (workOrderId != null) {
                        result.put(workOrderId, new CurrentTaskRow(
                                rs.getString("stage_code"),
                                rs.getString("task_status"),
                                rs.getString("claimed_by")));
                    }
                    return null;
                })
                .list();
        return result;
    }

    private record CurrentTaskRow(String stageCode, String taskStatus, String claimedBy) {}
}
