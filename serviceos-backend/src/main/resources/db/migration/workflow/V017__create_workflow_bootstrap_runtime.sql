CREATE TABLE wfl_workflow_instance (
    workflow_instance_id uuid         NOT NULL,
    tenant_id            varchar(64)  NOT NULL,
    project_id           uuid         NOT NULL,
    work_order_id        uuid         NOT NULL,
    configuration_bundle_id uuid      NOT NULL,
    workflow_definition_version_id uuid NOT NULL,
    workflow_key         varchar(120) NOT NULL,
    workflow_version     varchar(64)  NOT NULL,
    definition_digest    char(64)     NOT NULL,
    status               varchar(24)  NOT NULL,
    start_event_id       uuid         NOT NULL,
    correlation_id       varchar(128) NOT NULL,
    version              bigint       NOT NULL DEFAULT 1,
    started_at           timestamptz  NOT NULL,
    completed_at         timestamptz,
    CONSTRAINT pk_wfl_workflow_instance PRIMARY KEY (workflow_instance_id),
    CONSTRAINT uq_wfl_tenant_instance UNIQUE (tenant_id, workflow_instance_id),
    CONSTRAINT uq_wfl_work_order UNIQUE (tenant_id, work_order_id),
    CONSTRAINT uq_wfl_start_event UNIQUE (tenant_id, start_event_id),
    CONSTRAINT ck_wfl_definition_digest CHECK (definition_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_wfl_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'SUSPENDED', 'CANCELLED', 'FAILED')),
    CONSTRAINT ck_wfl_version CHECK (version >= 1)
);

CREATE INDEX ix_wfl_project_status
    ON wfl_workflow_instance (tenant_id, project_id, status, started_at);

CREATE TABLE wfl_stage_instance (
    stage_instance_id    uuid         NOT NULL,
    tenant_id            varchar(64)  NOT NULL,
    workflow_instance_id uuid         NOT NULL,
    work_order_id        uuid         NOT NULL,
    stage_code           varchar(100) NOT NULL,
    sequence_no          integer      NOT NULL,
    status               varchar(24)  NOT NULL,
    activation_event_id  uuid         NOT NULL,
    version              bigint       NOT NULL DEFAULT 1,
    activated_at         timestamptz  NOT NULL,
    completed_at         timestamptz,
    CONSTRAINT pk_wfl_stage_instance PRIMARY KEY (stage_instance_id),
    CONSTRAINT fk_wfl_stage_workflow FOREIGN KEY (tenant_id, workflow_instance_id)
        REFERENCES wfl_workflow_instance (tenant_id, workflow_instance_id),
    CONSTRAINT uq_wfl_stage_code UNIQUE (tenant_id, workflow_instance_id, stage_code, sequence_no),
    CONSTRAINT uq_wfl_stage_activation UNIQUE (tenant_id, activation_event_id),
    CONSTRAINT ck_wfl_stage_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_wfl_stage_status CHECK (
        status IN ('PENDING', 'ACTIVE', 'BLOCKED', 'COMPLETED', 'SKIPPED', 'CANCELLED')
    ),
    CONSTRAINT ck_wfl_stage_version CHECK (version >= 1)
);

CREATE INDEX ix_wfl_stage_work_order
    ON wfl_stage_instance (tenant_id, work_order_id, status, activated_at);
