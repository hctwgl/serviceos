-- M270：WAIT_EVENT 挂起/唤醒运行时。
-- WAITING 节点没有 Task；用独立订阅表保存事件类型与关联键，保证幂等唤醒。

ALTER TABLE wfl_node_instance
    DROP CONSTRAINT ck_wfl_node_status;

ALTER TABLE wfl_node_instance
    ADD CONSTRAINT ck_wfl_node_status
        CHECK (status IN ('ACTIVE', 'WAITING', 'COMPLETED', 'CANCELLED', 'FAILED'));

ALTER TABLE wfl_node_instance
    ALTER COLUMN task_id DROP NOT NULL;

ALTER TABLE wfl_node_instance
    DROP CONSTRAINT uq_wfl_node_task;

CREATE UNIQUE INDEX uq_wfl_node_task
    ON wfl_node_instance (tenant_id, task_id)
    WHERE task_id IS NOT NULL;

ALTER TABLE wfl_node_instance
    ADD CONSTRAINT ck_wfl_node_task_presence CHECK (
        (status = 'WAITING' AND task_id IS NULL)
        OR (status = 'ACTIVE' AND task_id IS NOT NULL)
        OR (status IN ('COMPLETED', 'CANCELLED', 'FAILED'))
    );

CREATE TABLE wfl_wait_subscription (
    wait_subscription_id       uuid         NOT NULL,
    tenant_id                  varchar(64)  NOT NULL,
    project_id                 uuid         NOT NULL,
    workflow_instance_id       uuid         NOT NULL,
    workflow_node_instance_id  uuid         NOT NULL,
    work_order_id              uuid         NOT NULL,
    node_id                    varchar(100) NOT NULL,
    wait_event_type            varchar(160) NOT NULL,
    correlation_key            varchar(300) NOT NULL,
    status                     varchar(24)  NOT NULL,
    activation_event_id        uuid         NOT NULL,
    wake_signal_id             varchar(128),
    version                    bigint       NOT NULL DEFAULT 1,
    activated_at               timestamptz  NOT NULL,
    completed_at               timestamptz,
    CONSTRAINT pk_wfl_wait_subscription PRIMARY KEY (wait_subscription_id),
    CONSTRAINT fk_wfl_wait_workflow FOREIGN KEY (tenant_id, workflow_instance_id)
        REFERENCES wfl_workflow_instance (tenant_id, workflow_instance_id),
    CONSTRAINT fk_wfl_wait_node FOREIGN KEY (workflow_node_instance_id)
        REFERENCES wfl_node_instance (workflow_node_instance_id),
    CONSTRAINT ck_wfl_wait_status CHECK (status IN ('WAITING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_wfl_wait_completion CHECK (
        (status = 'COMPLETED' AND wake_signal_id IS NOT NULL AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND wake_signal_id IS NULL AND completed_at IS NULL)
    ),
    CONSTRAINT ck_wfl_wait_version CHECK (version >= 1)
);

CREATE UNIQUE INDEX uq_wfl_wait_active_correlation
    ON wfl_wait_subscription (tenant_id, wait_event_type, correlation_key)
    WHERE status = 'WAITING';

CREATE UNIQUE INDEX uq_wfl_wait_wake_signal
    ON wfl_wait_subscription (tenant_id, wake_signal_id)
    WHERE wake_signal_id IS NOT NULL;

CREATE INDEX ix_wfl_wait_node
    ON wfl_wait_subscription (tenant_id, workflow_node_instance_id);
