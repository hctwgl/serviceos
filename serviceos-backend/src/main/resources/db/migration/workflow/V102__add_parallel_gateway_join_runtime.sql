-- M275：PARALLEL_GATEWAY 汇聚计数。
-- Fork 本身不落额外表；Join 用订阅式计数，按 (workflow_instance, join_node_id) 唯一 OPEN。

CREATE TABLE wfl_parallel_join (
    parallel_join_id     uuid         NOT NULL,
    tenant_id            varchar(64)  NOT NULL,
    workflow_instance_id uuid         NOT NULL,
    join_node_id         varchar(100) NOT NULL,
    expected_tokens      int          NOT NULL,
    arrived_tokens       int          NOT NULL DEFAULT 0,
    status               varchar(24)  NOT NULL,
    version              bigint       NOT NULL DEFAULT 1,
    opened_at            timestamptz  NOT NULL,
    completed_at         timestamptz,
    CONSTRAINT pk_wfl_parallel_join PRIMARY KEY (parallel_join_id),
    CONSTRAINT fk_wfl_parallel_join_workflow FOREIGN KEY (tenant_id, workflow_instance_id)
        REFERENCES wfl_workflow_instance (tenant_id, workflow_instance_id),
    CONSTRAINT ck_wfl_parallel_join_status CHECK (status IN ('OPEN', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_wfl_parallel_join_counts CHECK (
        expected_tokens >= 2
        AND arrived_tokens >= 0
        AND arrived_tokens <= expected_tokens
    ),
    CONSTRAINT ck_wfl_parallel_join_completion CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL AND arrived_tokens = expected_tokens)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    ),
    CONSTRAINT ck_wfl_parallel_join_version CHECK (version >= 1)
);

CREATE UNIQUE INDEX uq_wfl_parallel_join_open
    ON wfl_parallel_join (tenant_id, workflow_instance_id, join_node_id)
    WHERE status = 'OPEN';

CREATE TABLE wfl_parallel_join_token (
    parallel_join_id         uuid         NOT NULL,
    from_node_id             varchar(100) NOT NULL,
    source_node_instance_id  uuid         NOT NULL,
    activation_event_id      uuid         NOT NULL,
    arrived_at               timestamptz  NOT NULL,
    CONSTRAINT pk_wfl_parallel_join_token PRIMARY KEY (parallel_join_id, from_node_id),
    CONSTRAINT fk_wfl_parallel_join_token_join FOREIGN KEY (parallel_join_id)
        REFERENCES wfl_parallel_join (parallel_join_id)
);

CREATE INDEX ix_wfl_parallel_join_workflow
    ON wfl_parallel_join (tenant_id, workflow_instance_id, status);
