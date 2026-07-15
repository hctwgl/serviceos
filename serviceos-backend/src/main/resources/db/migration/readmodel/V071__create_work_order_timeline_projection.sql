CREATE TABLE rdm_work_order_timeline_entry (
    timeline_entry_id       uuid         NOT NULL,
    tenant_id               varchar(64)  NOT NULL,
    project_id              uuid         NOT NULL,
    work_order_id           uuid         NOT NULL,
    source_event_id         uuid         NOT NULL,
    source_module           varchar(32)  NOT NULL,
    event_type              varchar(120) NOT NULL,
    schema_version          integer      NOT NULL,
    category                varchar(24)  NOT NULL,
    resource_type           varchar(32)  NOT NULL,
    resource_id             uuid         NOT NULL,
    resource_version        bigint       NOT NULL,
    resource_code           varchar(160),
    outcome_code            varchar(100),
    actor_id                varchar(128),
    correlation_id          varchar(128) NOT NULL,
    display_template_code   varchar(120) NOT NULL,
    display_template_version integer     NOT NULL,
    occurred_at             timestamptz  NOT NULL,
    received_at             timestamptz  NOT NULL,
    CONSTRAINT pk_rdm_work_order_timeline PRIMARY KEY (timeline_entry_id),
    CONSTRAINT uq_rdm_work_order_timeline_source UNIQUE (tenant_id, source_event_id),
    CONSTRAINT ck_rdm_work_order_timeline_schema CHECK (schema_version > 0),
    CONSTRAINT ck_rdm_work_order_timeline_category CHECK (
        category IN ('WORK_ORDER', 'WORKFLOW', 'STAGE', 'TASK')
    ),
    CONSTRAINT ck_rdm_work_order_timeline_resource_version CHECK (resource_version > 0),
    CONSTRAINT ck_rdm_work_order_timeline_template_version CHECK (display_template_version > 0)
);

CREATE INDEX ix_rdm_work_order_timeline_page
    ON rdm_work_order_timeline_entry (
        tenant_id, work_order_id, occurred_at DESC, timeline_entry_id DESC
    );
