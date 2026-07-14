ALTER TABLE wfl_workflow_instance
    ADD COLUMN configuration_bundle_digest char(64);

UPDATE wfl_workflow_instance workflow
   SET configuration_bundle_digest = work_order.configuration_bundle_digest
  FROM wo_work_order work_order
 WHERE work_order.tenant_id = workflow.tenant_id
   AND work_order.id = workflow.work_order_id;

ALTER TABLE wfl_workflow_instance
    ALTER COLUMN configuration_bundle_digest SET NOT NULL,
    ADD CONSTRAINT ck_wfl_configuration_bundle_digest
        CHECK (configuration_bundle_digest ~ '^[0-9a-f]{64}$');

ALTER TABLE tsk_task
    ADD COLUMN configuration_bundle_id uuid,
    ADD COLUMN configuration_bundle_digest char(64);

UPDATE tsk_task task
   SET configuration_bundle_id = work_order.configuration_bundle_id,
       configuration_bundle_digest = work_order.configuration_bundle_digest
  FROM wo_work_order work_order
 WHERE work_order.tenant_id = task.tenant_id
   AND work_order.id = task.work_order_id;

ALTER TABLE tsk_task
    DROP CONSTRAINT ck_tsk_workflow_context_complete,
    ADD CONSTRAINT ck_tsk_configuration_bundle_digest
        CHECK (configuration_bundle_digest IS NULL OR configuration_bundle_digest ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT ck_tsk_workflow_context_complete CHECK (
        (project_id IS NULL AND work_order_id IS NULL AND workflow_instance_id IS NULL
            AND stage_instance_id IS NULL AND workflow_node_instance_id IS NULL
            AND workflow_node_id IS NULL AND workflow_definition_version_id IS NULL
            AND workflow_definition_digest IS NULL AND configuration_bundle_id IS NULL
            AND configuration_bundle_digest IS NULL)
        OR
        (project_id IS NOT NULL AND work_order_id IS NOT NULL AND workflow_instance_id IS NOT NULL
            AND stage_instance_id IS NOT NULL AND workflow_node_instance_id IS NOT NULL
            AND workflow_node_id IS NOT NULL AND workflow_definition_version_id IS NOT NULL
            AND workflow_definition_digest IS NOT NULL AND configuration_bundle_id IS NOT NULL
            AND configuration_bundle_digest IS NOT NULL)
    );

COMMENT ON COLUMN tsk_task.configuration_bundle_id IS
    '任务创建时冻结的 ConfigurationBundle ID；表单和资料解析不得改读最新配置';
COMMENT ON COLUMN tsk_task.configuration_bundle_digest IS
    '任务创建时冻结的 ConfigurationBundle manifest digest';

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('form.read', '查看任务锁定表单', 'NORMAL', now())
ON CONFLICT (capability_code) DO NOTHING;
