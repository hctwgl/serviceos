-- M278：多实例任务集合与 slot 完成计数。

CREATE TABLE wfl_multi_instance (
    multi_instance_id    uuid         NOT NULL,
    tenant_id            varchar(64)  NOT NULL,
    workflow_instance_id uuid         NOT NULL,
    node_id              varchar(100) NOT NULL,
    expected_instances   int          NOT NULL,
    completed_instances  int          NOT NULL DEFAULT 0,
    status               varchar(24)  NOT NULL,
    activation_event_id  uuid         NOT NULL,
    version              bigint       NOT NULL DEFAULT 1,
    opened_at            timestamptz  NOT NULL,
    completed_at         timestamptz,
    CONSTRAINT pk_wfl_multi_instance PRIMARY KEY (multi_instance_id),
    CONSTRAINT fk_wfl_mi_workflow FOREIGN KEY (tenant_id, workflow_instance_id)
        REFERENCES wfl_workflow_instance (tenant_id, workflow_instance_id),
    CONSTRAINT ck_wfl_mi_status CHECK (status IN ('OPEN', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_wfl_mi_counts CHECK (
        expected_instances >= 2
        AND completed_instances >= 0
        AND completed_instances <= expected_instances
    ),
    CONSTRAINT ck_wfl_mi_completion CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL
            AND completed_instances = expected_instances)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    ),
    CONSTRAINT ck_wfl_mi_version CHECK (version >= 1)
);

CREATE UNIQUE INDEX uq_wfl_mi_open
    ON wfl_multi_instance (tenant_id, workflow_instance_id, node_id)
    WHERE status = 'OPEN';

CREATE TABLE wfl_multi_instance_slot (
    multi_instance_id         uuid         NOT NULL,
    tenant_id                 varchar(64)  NOT NULL,
    instance_index            int          NOT NULL,
    workflow_node_instance_id uuid         NOT NULL,
    task_id                   uuid         NOT NULL,
    status                    varchar(24)  NOT NULL,
    completed_at              timestamptz,
    CONSTRAINT pk_wfl_mi_slot PRIMARY KEY (multi_instance_id, instance_index),
    CONSTRAINT fk_wfl_mi_slot_collection FOREIGN KEY (multi_instance_id)
        REFERENCES wfl_multi_instance (multi_instance_id),
    CONSTRAINT fk_wfl_mi_slot_node FOREIGN KEY (workflow_node_instance_id)
        REFERENCES wfl_node_instance (workflow_node_instance_id),
    CONSTRAINT uq_wfl_mi_slot_node UNIQUE (tenant_id, workflow_node_instance_id),
    CONSTRAINT ck_wfl_mi_slot_index CHECK (instance_index >= 0),
    CONSTRAINT ck_wfl_mi_slot_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_wfl_mi_slot_completion CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    )
);

CREATE INDEX ix_wfl_mi_workflow
    ON wfl_multi_instance (tenant_id, workflow_instance_id, status);
