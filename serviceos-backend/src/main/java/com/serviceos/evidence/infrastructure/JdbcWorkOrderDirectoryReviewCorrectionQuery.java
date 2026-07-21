package com.serviceos.evidence.infrastructure;

import com.serviceos.workorder.api.WorkOrderDirectoryReviewCorrectionQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * M447：按审核/整改运营桶筛选工单（task → work_order）。
 *
 * <p>REVIEW_OPEN：OPEN ReviewCase；CORRECTION_ACTIVE：OPEN|IN_PROGRESS|RESUBMITTED CorrectionCase。</p>
 */
@Component
final class JdbcWorkOrderDirectoryReviewCorrectionQuery implements WorkOrderDirectoryReviewCorrectionQuery {

    private static final String FILTER_REVIEW_OPEN_TENANT_WIDE = """
            SELECT DISTINCT t.work_order_id
              FROM evd_review_case r
              JOIN tsk_task t
                ON t.tenant_id = r.tenant_id
               AND t.task_id = r.task_id
             WHERE r.tenant_id = :tenantId
               AND r.status = 'OPEN'
               AND t.work_order_id IS NOT NULL
            """;

    private static final String FILTER_REVIEW_OPEN_PROJECT_SCOPED = """
            SELECT DISTINCT t.work_order_id
              FROM evd_review_case r
              JOIN tsk_task t
                ON t.tenant_id = r.tenant_id
               AND t.task_id = r.task_id
             WHERE r.tenant_id = :tenantId
               AND r.status = 'OPEN'
               AND r.project_id IN (:projectIds)
               AND t.work_order_id IS NOT NULL
            """;

    private static final String FILTER_CORRECTION_ACTIVE_TENANT_WIDE = """
            SELECT DISTINCT t.work_order_id
              FROM evd_correction_case c
              JOIN tsk_task t
                ON t.tenant_id = c.tenant_id
               AND t.task_id = c.task_id
             WHERE c.tenant_id = :tenantId
               AND c.status IN ('OPEN', 'IN_PROGRESS', 'RESUBMITTED')
               AND t.work_order_id IS NOT NULL
            """;

    private static final String FILTER_CORRECTION_ACTIVE_PROJECT_SCOPED = """
            SELECT DISTINCT t.work_order_id
              FROM evd_correction_case c
              JOIN tsk_task t
                ON t.tenant_id = c.tenant_id
               AND t.task_id = c.task_id
             WHERE c.tenant_id = :tenantId
               AND c.status IN ('OPEN', 'IN_PROGRESS', 'RESUBMITTED')
               AND c.project_id IN (:projectIds)
               AND t.work_order_id IS NOT NULL
            """;

    private final JdbcClient jdbc;

    JdbcWorkOrderDirectoryReviewCorrectionQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> findWorkOrderIdsByReviewCorrectionStatus(
            String tenantId,
            String reviewCorrectionStatus,
            boolean tenantWide,
            Collection<UUID> projectIds
    ) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(reviewCorrectionStatus, "reviewCorrectionStatus must not be null");
        Objects.requireNonNull(projectIds, "projectIds must not be null");
        if (!tenantWide && projectIds.isEmpty()) {
            return List.of();
        }
        String sql = switch (reviewCorrectionStatus) {
            case "REVIEW_OPEN" -> tenantWide
                    ? FILTER_REVIEW_OPEN_TENANT_WIDE
                    : FILTER_REVIEW_OPEN_PROJECT_SCOPED;
            case "CORRECTION_ACTIVE" -> tenantWide
                    ? FILTER_CORRECTION_ACTIVE_TENANT_WIDE
                    : FILTER_CORRECTION_ACTIVE_PROJECT_SCOPED;
            default -> throw new IllegalArgumentException("reviewCorrectionStatus is invalid");
        };
        List<UUID> ids = new ArrayList<>();
        var spec = jdbc.sql(sql).param("tenantId", tenantId);
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
}
