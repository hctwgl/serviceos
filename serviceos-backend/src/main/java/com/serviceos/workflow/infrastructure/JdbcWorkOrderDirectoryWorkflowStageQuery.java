package com.serviceos.workflow.infrastructure;

import com.serviceos.workorder.api.WorkOrderDirectoryWorkflowStageQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 由工作流权威 Stage 实例提供工单当前阶段。
 *
 * <p>REVIEW_TASK 与 WAIT_EVENT 不创建 {@code tsk_task}，因此不能从任务表推断。
 * 同一工单若存在并行 ACTIVE Stage，单值目录按最新 sequence/activatedAt 稳定选择；
 * 完整并行阶段仍通过 WorkflowExecutionQueryService 查询。</p>
 */
@Component
final class JdbcWorkOrderDirectoryWorkflowStageQuery
        implements WorkOrderDirectoryWorkflowStageQuery {

    private static final String SIDE_CAR_SQL = """
            SELECT DISTINCT ON (stage.work_order_id)
                   stage.work_order_id AS work_order_id,
                   stage.stage_code AS stage_code
              FROM wfl_stage_instance stage
              JOIN wfl_workflow_instance workflow
                ON workflow.tenant_id = stage.tenant_id
               AND workflow.workflow_instance_id = stage.workflow_instance_id
             WHERE stage.tenant_id = :tenantId
               AND stage.work_order_id IN (:workOrderIds)
               AND stage.status = 'ACTIVE'
               AND workflow.status = 'ACTIVE'
             ORDER BY stage.work_order_id, stage.sequence_no DESC,
                      stage.activated_at DESC, stage.stage_instance_id DESC
            """;

    private static final String FILTER_TENANT_WIDE = """
            SELECT DISTINCT stage.work_order_id AS work_order_id
              FROM wfl_stage_instance stage
              JOIN wfl_workflow_instance workflow
                ON workflow.tenant_id = stage.tenant_id
               AND workflow.workflow_instance_id = stage.workflow_instance_id
             WHERE stage.tenant_id = :tenantId
               AND stage.status = 'ACTIVE'
               AND stage.stage_code = :stageCode
               AND workflow.status = 'ACTIVE'
            """;

    private static final String FILTER_PROJECT_SCOPED = FILTER_TENANT_WIDE
            + " AND workflow.project_id IN (:projectIds)";

    private final JdbcClient jdbc;

    JdbcWorkOrderDirectoryWorkflowStageQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, String> findCurrentStageCodes(
            String tenantId,
            Collection<UUID> workOrderIds
    ) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workOrderIds, "workOrderIds must not be null");
        if (workOrderIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> result = new HashMap<>();
        jdbc.sql(SIDE_CAR_SQL)
                .param("tenantId", tenantId)
                .param("workOrderIds", List.copyOf(workOrderIds))
                .query((rs, rowNum) -> {
                    result.put(
                            rs.getObject("work_order_id", UUID.class),
                            rs.getString("stage_code"));
                    return null;
                })
                .list();
        return Map.copyOf(result);
    }

    @Override
    public List<UUID> findWorkOrderIdsByCurrentStageCode(
            String tenantId,
            String stageCode,
            boolean tenantWide,
            Collection<UUID> projectIds
    ) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(stageCode, "stageCode must not be null");
        Objects.requireNonNull(projectIds, "projectIds must not be null");
        if (!tenantWide && projectIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> result = new LinkedHashSet<>();
        var spec = jdbc.sql(tenantWide ? FILTER_TENANT_WIDE : FILTER_PROJECT_SCOPED)
                .param("tenantId", tenantId)
                .param("stageCode", stageCode);
        if (!tenantWide) {
            spec = spec.param("projectIds", List.copyOf(projectIds));
        }
        spec.query((rs, rowNum) -> {
                    result.add(rs.getObject("work_order_id", UUID.class));
                    return null;
                })
                .list();
        return List.copyOf(result);
    }
}
