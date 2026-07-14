ALTER TABLE tsk_task
    ADD COLUMN form_ref varchar(120),
    ADD CONSTRAINT ck_tsk_form_ref_workflow_context CHECK (
        form_ref IS NULL OR workflow_definition_version_id IS NOT NULL
    );

COMMENT ON COLUMN tsk_task.form_ref IS
    '任务创建时从锁定 WorkflowVersion 冻结的 Form assetKey；不得改为读取当前最新配置';
