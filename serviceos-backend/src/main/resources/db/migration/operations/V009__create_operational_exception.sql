CREATE TABLE ops_operational_exception (
    exception_id       uuid         NOT NULL,
    tenant_id          varchar(64)  NOT NULL,
    source_type        varchar(80)  NOT NULL,
    source_id          varchar(128) NOT NULL,
    source_attempt_id  uuid         NOT NULL,
    source_task_type   varchar(120) NOT NULL,
    category_code      varchar(80)  NOT NULL,
    severity_code      varchar(16)  NOT NULL,
    error_code         varchar(100) NOT NULL,
    status             varchar(24)  NOT NULL,
    handling_task_id   uuid,
    correlation_id     varchar(128) NOT NULL,
    opened_at          timestamptz  NOT NULL,
    resolved_at        timestamptz,
    CONSTRAINT pk_ops_operational_exception PRIMARY KEY (exception_id),
    CONSTRAINT uq_ops_exception_source_attempt
        UNIQUE (tenant_id, source_type, source_id, source_attempt_id),
    CONSTRAINT ck_ops_exception_severity CHECK (severity_code IN ('P0', 'P1', 'P2', 'P3')),
    CONSTRAINT ck_ops_exception_status CHECK (status IN ('OPEN', 'RESOLVED'))
);

CREATE INDEX ix_ops_exception_open
    ON ops_operational_exception (tenant_id, severity_code, opened_at)
    WHERE status = 'OPEN';
