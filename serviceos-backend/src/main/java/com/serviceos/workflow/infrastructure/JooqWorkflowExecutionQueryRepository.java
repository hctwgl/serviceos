package com.serviceos.workflow.infrastructure;

import com.serviceos.jooq.generated.tables.WflStageInstance;
import com.serviceos.jooq.generated.tables.WflWorkflowInstance;
import com.serviceos.workflow.api.StageInstanceView;
import com.serviceos.workflow.api.WorkflowInstanceView;
import com.serviceos.workflow.application.WorkflowExecutionQueryRepository;
import org.jooq.DSLContext;
import org.jooq.Records;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.WflStageInstance.WFL_STAGE_INSTANCE;
import static com.serviceos.jooq.generated.tables.WflWorkflowInstance.WFL_WORKFLOW_INSTANCE;

/**
 * 流程执行查询（jOOQ 实现）：按租户与工单读取流程/阶段实例投影。
 */
@Repository
final class JooqWorkflowExecutionQueryRepository implements WorkflowExecutionQueryRepository {
    private final DSLContext dsl;

    JooqWorkflowExecutionQueryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<WorkflowInstanceView> findWorkflow(String tenantId, UUID workOrderId) {
        WflWorkflowInstance workflow = WFL_WORKFLOW_INSTANCE;
        return dsl.select(
                        workflow.WORKFLOW_INSTANCE_ID,
                        workflow.PROJECT_ID,
                        workflow.WORK_ORDER_ID,
                        workflow.CONFIGURATION_BUNDLE_ID,
                        workflow.WORKFLOW_DEFINITION_VERSION_ID,
                        workflow.WORKFLOW_KEY,
                        workflow.WORKFLOW_VERSION,
                        workflow.DEFINITION_DIGEST,
                        workflow.STATUS,
                        workflow.VERSION,
                        workflow.STARTED_AT,
                        workflow.COMPLETED_AT)
                .from(workflow)
                .where(workflow.TENANT_ID.eq(tenantId))
                .and(workflow.WORK_ORDER_ID.eq(workOrderId))
                .fetchOptional(Records.mapping(WorkflowInstanceView::new));
    }

    @Override
    public List<StageInstanceView> findStages(String tenantId, UUID workOrderId) {
        WflStageInstance stage = WFL_STAGE_INSTANCE;
        return dsl.select(
                        stage.STAGE_INSTANCE_ID,
                        stage.WORKFLOW_INSTANCE_ID,
                        stage.WORK_ORDER_ID,
                        stage.STAGE_CODE,
                        stage.SEQUENCE_NO,
                        stage.STATUS,
                        stage.VERSION,
                        stage.ACTIVATED_AT,
                        stage.COMPLETED_AT)
                .from(stage)
                .where(stage.TENANT_ID.eq(tenantId))
                .and(stage.WORK_ORDER_ID.eq(workOrderId))
                .orderBy(stage.SEQUENCE_NO, stage.STAGE_INSTANCE_ID)
                .fetch(Records.mapping(StageInstanceView::new));
    }
}
