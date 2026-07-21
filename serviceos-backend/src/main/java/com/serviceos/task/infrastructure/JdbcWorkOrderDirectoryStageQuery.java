package com.serviceos.task.infrastructure;

import com.serviceos.workorder.api.WorkOrderDirectoryAssigneeQuery;
import com.serviceos.workorder.api.WorkOrderDirectoryStageQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * M432/M433：批量解析工单当前阶段码与认领主体。
 *
 * <p>使用 PostgreSQL {@code DISTINCT ON}，按 created_at/task_id 取最早 ACTIVE 任务，
 * 与工作区 currentTaskSummary 在列表顺序下的 findFirst 口径一致。</p>
 */
@Component
final class JdbcWorkOrderDirectoryStageQuery
        implements WorkOrderDirectoryStageQuery, WorkOrderDirectoryAssigneeQuery {

    private static final String SQL = """
            SELECT DISTINCT ON (work_order_id)
                   work_order_id AS work_order_id,
                   stage_code AS stage_code,
                   claimed_by AS claimed_by
              FROM tsk_task
             WHERE tenant_id = :tenantId
               AND work_order_id IN (:workOrderIds)
               AND status IN (
                   'READY', 'PENDING', 'CLAIMED', 'RUNNING',
                   'RETRY_WAIT', 'MANUAL_INTERVENTION'
               )
             ORDER BY work_order_id, created_at ASC, task_id ASC
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

    private Map<UUID, CurrentTaskRow> loadCurrentTasks(String tenantId, Collection<UUID> workOrderIds) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workOrderIds, "workOrderIds must not be null");
        if (workOrderIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = List.copyOf(workOrderIds);
        Map<UUID, CurrentTaskRow> result = new HashMap<>();
        jdbc.sql(SQL)
                .param("tenantId", tenantId)
                .param("workOrderIds", ids)
                .query((rs, rowNum) -> {
                    UUID workOrderId = rs.getObject("work_order_id", UUID.class);
                    if (workOrderId != null) {
                        result.put(workOrderId, new CurrentTaskRow(
                                rs.getString("stage_code"),
                                rs.getString("claimed_by")));
                    }
                    return null;
                })
                .list();
        return result;
    }

    private record CurrentTaskRow(String stageCode, String claimedBy) {}
}
