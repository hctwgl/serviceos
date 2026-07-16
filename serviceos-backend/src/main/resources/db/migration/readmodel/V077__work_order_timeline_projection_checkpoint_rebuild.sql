-- M84：工单时间线投影 checkpoint / dead letter / rebuild generation。
-- 重建使用新 generation 写入，验证后原子切换；不得先清空当前可读投影。

ALTER TABLE rdm_work_order_timeline_entry
    ADD COLUMN rebuild_generation integer;

UPDATE rdm_work_order_timeline_entry
   SET rebuild_generation = 1
 WHERE rebuild_generation IS NULL;

ALTER TABLE rdm_work_order_timeline_entry
    ALTER COLUMN rebuild_generation SET NOT NULL,
    DROP CONSTRAINT uq_rdm_work_order_timeline_source,
    ADD CONSTRAINT uq_rdm_work_order_timeline_source_generation
        UNIQUE (tenant_id, source_event_id, rebuild_generation),
    ADD CONSTRAINT ck_rdm_work_order_timeline_rebuild_generation
        CHECK (rebuild_generation >= 1);

DROP INDEX IF EXISTS ix_rdm_work_order_timeline_page;

CREATE INDEX ix_rdm_work_order_timeline_page
    ON rdm_work_order_timeline_entry (
        tenant_id, work_order_id, rebuild_generation,
        occurred_at DESC, timeline_entry_id DESC
    );

CREATE TABLE rdm_projection_state (
    projection_code             varchar(120) NOT NULL,
    schema_version              integer      NOT NULL,
    active_generation           integer      NOT NULL,
    status                      varchar(24)  NOT NULL,
    last_rebuild_started_at     timestamptz,
    last_rebuild_completed_at   timestamptz,
    updated_at                  timestamptz  NOT NULL,
    CONSTRAINT pk_rdm_projection_state PRIMARY KEY (projection_code),
    CONSTRAINT ck_rdm_projection_state_schema CHECK (schema_version > 0),
    CONSTRAINT ck_rdm_projection_state_generation CHECK (active_generation >= 1),
    CONSTRAINT ck_rdm_projection_state_status CHECK (
        status IN ('RUNNING', 'LAGGING', 'REBUILDING', 'FAILED')
    )
);

CREATE TABLE rdm_projection_checkpoint (
    projection_code         varchar(120) NOT NULL,
    tenant_id               varchar(64)  NOT NULL,
    partition_key           varchar(160) NOT NULL,
    rebuild_generation      integer      NOT NULL,
    last_source_outbox_id   uuid,
    last_occurred_at        timestamptz,
    processed_at            timestamptz  NOT NULL,
    status                  varchar(24)  NOT NULL,
    error_code              varchar(100),
    CONSTRAINT pk_rdm_projection_checkpoint
        PRIMARY KEY (projection_code, tenant_id, partition_key, rebuild_generation),
    CONSTRAINT ck_rdm_projection_checkpoint_generation CHECK (rebuild_generation >= 1),
    CONSTRAINT ck_rdm_projection_checkpoint_status CHECK (
        status IN ('RUNNING', 'LAGGING', 'FAILED', 'REBUILDING')
    )
);

CREATE TABLE rdm_projection_dead_letter (
    dead_letter_id          uuid         NOT NULL,
    projection_code         varchar(120) NOT NULL,
    tenant_id               varchar(64)  NOT NULL,
    rebuild_generation      integer      NOT NULL,
    event_id                uuid         NOT NULL,
    payload_digest          char(64)     NOT NULL,
    event_type              varchar(160) NOT NULL,
    schema_version          integer      NOT NULL,
    error_code              varchar(100) NOT NULL,
    attempt_count           integer      NOT NULL,
    first_failed_at         timestamptz  NOT NULL,
    last_failed_at          timestamptz  NOT NULL,
    resolved_at             timestamptz,
    resolution              varchar(32),
    CONSTRAINT pk_rdm_projection_dead_letter PRIMARY KEY (dead_letter_id),
    CONSTRAINT uq_rdm_projection_dead_letter_event
        UNIQUE (projection_code, tenant_id, rebuild_generation, event_id),
    CONSTRAINT ck_rdm_projection_dead_letter_generation CHECK (rebuild_generation >= 1),
    CONSTRAINT ck_rdm_projection_dead_letter_schema CHECK (schema_version > 0),
    CONSTRAINT ck_rdm_projection_dead_letter_attempt CHECK (attempt_count >= 1),
    CONSTRAINT ck_rdm_projection_dead_letter_resolution CHECK (
        resolution IS NULL OR resolution IN ('REPLAYED', 'DISCARDED', 'PENDING')
    )
);

CREATE INDEX ix_rdm_projection_dead_letter_open
    ON rdm_projection_dead_letter (projection_code, tenant_id, rebuild_generation)
    WHERE resolved_at IS NULL;

INSERT INTO rdm_projection_state (
    projection_code, schema_version, active_generation, status, updated_at
) VALUES (
    'work-order-core-timeline.v1', 1, 1, 'RUNNING', now()
);
