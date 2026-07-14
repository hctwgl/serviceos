-- M17 没有节点实例，也没有 TaskCompleted 领域事件。若升级时已经存在终态工作流任务，
-- 无法可靠推导 completion_event_id，迁移必须失败关闭并要求先做显式数据修复。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM tsk_task
         WHERE workflow_node_instance_id IS NOT NULL
           AND status NOT IN ('PENDING', 'READY', 'CLAIMED', 'RETRY_WAIT')
    ) THEN
        RAISE EXCEPTION
            'V018 requires all existing workflow tasks to be non-terminal; repair legacy workflow tasks before migration';
    END IF;
END $$;

ALTER TABLE wfl_stage_instance
    ADD CONSTRAINT uq_wfl_tenant_stage UNIQUE (tenant_id, stage_instance_id);

CREATE TABLE wfl_node_instance (
    workflow_node_instance_id uuid         NOT NULL,
    tenant_id                  varchar(64)  NOT NULL,
    workflow_instance_id       uuid         NOT NULL,
    stage_instance_id          uuid         NOT NULL,
    work_order_id              uuid         NOT NULL,
    node_id                    varchar(100) NOT NULL,
    task_id                    uuid         NOT NULL,
    status                     varchar(24)  NOT NULL,
    activation_event_id        uuid         NOT NULL,
    completion_event_id        uuid,
    version                    bigint       NOT NULL DEFAULT 1,
    activated_at               timestamptz  NOT NULL,
    completed_at               timestamptz,
    CONSTRAINT pk_wfl_node_instance PRIMARY KEY (workflow_node_instance_id),
    CONSTRAINT fk_wfl_node_workflow FOREIGN KEY (tenant_id, workflow_instance_id)
        REFERENCES wfl_workflow_instance (tenant_id, workflow_instance_id),
    CONSTRAINT fk_wfl_node_stage FOREIGN KEY (tenant_id, stage_instance_id)
        REFERENCES wfl_stage_instance (tenant_id, stage_instance_id),
    CONSTRAINT uq_wfl_node_task UNIQUE (tenant_id, task_id),
    CONSTRAINT uq_wfl_node_activation UNIQUE (tenant_id, activation_event_id, workflow_node_instance_id),
    CONSTRAINT uq_wfl_node_completion UNIQUE (tenant_id, completion_event_id),
    CONSTRAINT ck_wfl_node_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED', 'FAILED')),
    CONSTRAINT ck_wfl_node_completion CHECK (
        (status = 'COMPLETED' AND completion_event_id IS NOT NULL AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completion_event_id IS NULL AND completed_at IS NULL)
    ),
    CONSTRAINT ck_wfl_node_version CHECK (version >= 1)
);

INSERT INTO wfl_node_instance (
    workflow_node_instance_id, tenant_id, workflow_instance_id, stage_instance_id,
    work_order_id, node_id, task_id, status, activation_event_id, version, activated_at
)
SELECT task.workflow_node_instance_id, task.tenant_id, task.workflow_instance_id,
       task.stage_instance_id, task.work_order_id, task.workflow_node_id, task.task_id,
       'ACTIVE', workflow.start_event_id, 1, task.created_at
  FROM tsk_task task
  JOIN wfl_workflow_instance workflow
    ON workflow.tenant_id = task.tenant_id
   AND workflow.workflow_instance_id = task.workflow_instance_id
 WHERE task.workflow_node_instance_id IS NOT NULL;

CREATE INDEX ix_wfl_node_active
    ON wfl_node_instance (tenant_id, workflow_instance_id, status, activated_at);
