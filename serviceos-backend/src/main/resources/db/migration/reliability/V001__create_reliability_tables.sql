CREATE TABLE rel_idempotency_record (
    tenant_id          varchar(64)  NOT NULL,
    operation_type     varchar(100) NOT NULL,
    idempotency_key    varchar(160) NOT NULL,
    request_digest     char(64)     NOT NULL,
    actor_id           varchar(128) NOT NULL,
    status             varchar(32)  NOT NULL,
    resource_id        varchar(128),
    response_digest    char(64),
    started_at         timestamptz  NOT NULL,
    completed_at       timestamptz,
    expires_at         timestamptz  NOT NULL,
    CONSTRAINT pk_rel_idempotency_record
        PRIMARY KEY (tenant_id, operation_type, idempotency_key),
    CONSTRAINT ck_rel_idempotency_status
        CHECK (status IN ('IN_PROGRESS', 'SUCCEEDED', 'FAILED_RETRYABLE', 'FAILED_FINAL'))
);

CREATE INDEX ix_rel_idempotency_expiry
    ON rel_idempotency_record (expires_at)
    WHERE status <> 'SUCCEEDED';

CREATE TABLE rel_outbox_event (
    outbox_id           uuid         NOT NULL,
    module_name         varchar(64)  NOT NULL,
    event_type          varchar(160) NOT NULL,
    schema_version      integer      NOT NULL,
    aggregate_type      varchar(100) NOT NULL,
    aggregate_id        varchar(128) NOT NULL,
    aggregate_version   bigint       NOT NULL,
    tenant_id           varchar(64)  NOT NULL,
    correlation_id      varchar(128) NOT NULL,
    causation_id        varchar(160) NOT NULL,
    partition_key       varchar(160) NOT NULL,
    payload             jsonb        NOT NULL,
    payload_digest      char(64)     NOT NULL,
    status              varchar(24)  NOT NULL,
    available_at        timestamptz  NOT NULL,
    claim_owner         varchar(128),
    claim_until         timestamptz,
    attempt_count       integer      NOT NULL DEFAULT 0,
    last_error_code     varchar(100),
    occurred_at         timestamptz  NOT NULL,
    created_at          timestamptz  NOT NULL,
    published_at        timestamptz,
    CONSTRAINT pk_rel_outbox_event PRIMARY KEY (outbox_id),
    CONSTRAINT ck_rel_outbox_status
        CHECK (status IN ('PENDING', 'CLAIMED', 'PUBLISHED', 'FAILED', 'DEAD')),
    CONSTRAINT ck_rel_outbox_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX ix_rel_outbox_claim
    ON rel_outbox_event (available_at, created_at)
    WHERE status IN ('PENDING', 'CLAIMED', 'FAILED');

CREATE INDEX ix_rel_outbox_aggregate
    ON rel_outbox_event (tenant_id, aggregate_type, aggregate_id, aggregate_version);
