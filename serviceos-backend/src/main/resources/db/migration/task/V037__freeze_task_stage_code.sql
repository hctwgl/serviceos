ALTER TABLE tsk_task
    ADD COLUMN stage_code varchar(64);

-- 存量 Workflow Task 只能从其已冻结的 StageInstance 回填，不能从当前 Workflow 定义重新推导。
UPDATE tsk_task task
   SET stage_code = stage.stage_code
  FROM wfl_stage_instance stage
 WHERE stage.tenant_id = task.tenant_id
   AND stage.stage_instance_id = task.stage_instance_id
   AND task.stage_instance_id IS NOT NULL;

ALTER TABLE tsk_task
    DROP CONSTRAINT ck_tsk_workflow_context_complete,
    ADD CONSTRAINT uq_tsk_task_evidence_scope UNIQUE (tenant_id, task_id, project_id),
    ADD CONSTRAINT ck_tsk_stage_code CHECK (
        stage_code IS NULL OR stage_code ~ '^[A-Z][A-Z0-9_]*$'
    ),
    ADD CONSTRAINT ck_tsk_workflow_context_complete CHECK (
        (project_id IS NULL AND work_order_id IS NULL AND workflow_instance_id IS NULL
            AND stage_instance_id IS NULL AND workflow_node_instance_id IS NULL
            AND workflow_node_id IS NULL AND workflow_definition_version_id IS NULL
            AND workflow_definition_digest IS NULL AND configuration_bundle_id IS NULL
            AND configuration_bundle_digest IS NULL AND stage_code IS NULL)
        OR
        (project_id IS NOT NULL AND work_order_id IS NOT NULL AND workflow_instance_id IS NOT NULL
            AND stage_instance_id IS NOT NULL AND workflow_node_instance_id IS NOT NULL
            AND workflow_node_id IS NOT NULL AND workflow_definition_version_id IS NOT NULL
            AND workflow_definition_digest IS NOT NULL AND configuration_bundle_id IS NOT NULL
            AND configuration_bundle_digest IS NOT NULL AND stage_code IS NOT NULL)
    );

COMMENT ON COLUMN tsk_task.stage_code IS
    'Task 创建时冻结的 Workflow Stage code；表单/资料运行时不得按 taskType 猜测阶段';
