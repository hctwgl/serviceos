-- M277：SUB_PROCESS。根流程对 work_order 仍唯一；子流程 instance_role=SUBPROCESS。

ALTER TABLE wfl_workflow_instance
    DROP CONSTRAINT uq_wfl_work_order;

ALTER TABLE wfl_workflow_instance
    ADD COLUMN instance_role varchar(24) NOT NULL DEFAULT 'ROOT';

ALTER TABLE wfl_workflow_instance
    ADD CONSTRAINT ck_wfl_instance_role CHECK (instance_role IN ('ROOT', 'SUBPROCESS'));

CREATE UNIQUE INDEX uq_wfl_root_work_order
    ON wfl_workflow_instance (tenant_id, work_order_id)
    WHERE instance_role = 'ROOT';

CREATE TABLE wfl_subprocess_link (
    subprocess_link_id           uuid         NOT NULL,
    tenant_id                    varchar(64)  NOT NULL,
    parent_workflow_instance_id  uuid         NOT NULL,
    parent_node_instance_id      uuid         NOT NULL,
    parent_node_id               varchar(100) NOT NULL,
    child_workflow_instance_id   uuid         NOT NULL,
    child_workflow_key           varchar(160) NOT NULL,
    child_definition_version_id  uuid         NOT NULL,
    child_definition_digest      char(64)     NOT NULL,
    status                       varchar(24)  NOT NULL,
    activation_event_id          uuid         NOT NULL,
    completion_event_id          uuid,
    version                      bigint       NOT NULL DEFAULT 1,
    started_at                   timestamptz  NOT NULL,
    completed_at                 timestamptz,
    CONSTRAINT pk_wfl_subprocess_link PRIMARY KEY (subprocess_link_id),
    CONSTRAINT fk_wfl_subprocess_parent_workflow FOREIGN KEY (tenant_id, parent_workflow_instance_id)
        REFERENCES wfl_workflow_instance (tenant_id, workflow_instance_id),
    CONSTRAINT fk_wfl_subprocess_parent_node FOREIGN KEY (parent_node_instance_id)
        REFERENCES wfl_node_instance (workflow_node_instance_id),
    CONSTRAINT fk_wfl_subprocess_child_workflow FOREIGN KEY (tenant_id, child_workflow_instance_id)
        REFERENCES wfl_workflow_instance (tenant_id, workflow_instance_id),
    CONSTRAINT uq_wfl_subprocess_child UNIQUE (tenant_id, child_workflow_instance_id),
    CONSTRAINT uq_wfl_subprocess_parent_node UNIQUE (tenant_id, parent_node_instance_id),
    CONSTRAINT ck_wfl_subprocess_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_wfl_subprocess_digest CHECK (child_definition_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_wfl_subprocess_completion CHECK (
        (status = 'COMPLETED' AND completion_event_id IS NOT NULL AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completion_event_id IS NULL AND completed_at IS NULL)
    ),
    CONSTRAINT ck_wfl_subprocess_version CHECK (version >= 1)
);

CREATE INDEX ix_wfl_subprocess_parent
    ON wfl_subprocess_link (tenant_id, parent_workflow_instance_id, status);
