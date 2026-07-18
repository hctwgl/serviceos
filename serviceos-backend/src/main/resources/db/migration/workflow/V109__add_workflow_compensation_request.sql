-- M280：取消时配置化补偿请求（对已完成节点创建补偿任务的审计与幂等）。

CREATE TABLE wfl_compensation_request (
    compensation_request_id   uuid         NOT NULL,
    tenant_id                 varchar(64)  NOT NULL,
    workflow_instance_id      uuid         NOT NULL,
    work_order_id             uuid         NOT NULL,
    source_node_instance_id   uuid         NOT NULL,
    source_node_id            varchar(100) NOT NULL,
    compensation_task_type    varchar(100) NOT NULL,
    compensation_stage_code   varchar(100) NOT NULL,
    compensation_task_id      uuid         NOT NULL,
    cancel_event_id           uuid         NOT NULL,
    status                    varchar(24)  NOT NULL,
    version                   bigint       NOT NULL DEFAULT 1,
    requested_at              timestamptz  NOT NULL,
    CONSTRAINT pk_wfl_compensation_request PRIMARY KEY (compensation_request_id),
    CONSTRAINT fk_wfl_compensation_workflow
        FOREIGN KEY (tenant_id, workflow_instance_id)
        REFERENCES wfl_workflow_instance (tenant_id, workflow_instance_id),
    CONSTRAINT fk_wfl_compensation_source_node
        FOREIGN KEY (source_node_instance_id)
        REFERENCES wfl_node_instance (workflow_node_instance_id),
    CONSTRAINT uq_wfl_compensation_cancel_source
        UNIQUE (tenant_id, cancel_event_id, source_node_instance_id),
    CONSTRAINT ck_wfl_compensation_status CHECK (status IN ('REQUESTED', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_wfl_compensation_version CHECK (version >= 1)
);

CREATE INDEX ix_wfl_compensation_work_order
    ON wfl_compensation_request (tenant_id, work_order_id, status);
