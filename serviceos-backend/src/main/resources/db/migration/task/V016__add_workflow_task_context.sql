ALTER TABLE tsk_task
    ADD COLUMN project_id uuid,
    ADD COLUMN work_order_id uuid,
    ADD COLUMN workflow_instance_id uuid,
    ADD COLUMN stage_instance_id uuid,
    ADD COLUMN workflow_node_instance_id uuid,
    ADD COLUMN workflow_node_id varchar(100),
    ADD COLUMN workflow_definition_version_id uuid,
    ADD COLUMN workflow_definition_digest char(64),
    ADD CONSTRAINT ck_tsk_workflow_definition_digest
        CHECK (workflow_definition_digest IS NULL OR workflow_definition_digest ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT ck_tsk_workflow_context_complete CHECK (
        (project_id IS NULL AND work_order_id IS NULL AND workflow_instance_id IS NULL
            AND stage_instance_id IS NULL AND workflow_node_instance_id IS NULL
            AND workflow_node_id IS NULL AND workflow_definition_version_id IS NULL
            AND workflow_definition_digest IS NULL)
        OR
        (project_id IS NOT NULL AND work_order_id IS NOT NULL AND workflow_instance_id IS NOT NULL
            AND stage_instance_id IS NOT NULL AND workflow_node_instance_id IS NOT NULL
            AND workflow_node_id IS NOT NULL AND workflow_definition_version_id IS NOT NULL
            AND workflow_definition_digest IS NOT NULL)
    );

CREATE UNIQUE INDEX uq_tsk_workflow_node_instance
    ON tsk_task (tenant_id, workflow_node_instance_id)
    WHERE workflow_node_instance_id IS NOT NULL;

CREATE INDEX ix_tsk_work_order
    ON tsk_task (tenant_id, work_order_id, created_at)
    WHERE work_order_id IS NOT NULL;

CREATE INDEX ix_tsk_stage_instance
    ON tsk_task (tenant_id, stage_instance_id, created_at)
    WHERE stage_instance_id IS NOT NULL;
