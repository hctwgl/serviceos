CREATE TABLE tsk_task (
    task_id             uuid         NOT NULL,
    tenant_id           varchar(64)  NOT NULL,
    task_type           varchar(120) NOT NULL,
    task_kind           varchar(24)  NOT NULL,
    business_key        varchar(180) NOT NULL,
    payload_ref         varchar(500),
    payload_digest      char(64)     NOT NULL,
    priority            integer      NOT NULL,
    status              varchar(32)  NOT NULL,
    next_run_at         timestamptz  NOT NULL,
    claim_owner         varchar(128),
    claim_until         timestamptz,
    current_attempt_id  uuid,
    attempt_count       integer      NOT NULL DEFAULT 0,
    max_attempts        integer      NOT NULL,
    last_error_code     varchar(100),
    correlation_id      varchar(128) NOT NULL,
    version             bigint       NOT NULL DEFAULT 1,
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    completed_at        timestamptz,
    CONSTRAINT pk_tsk_task PRIMARY KEY (task_id),
    CONSTRAINT uq_tsk_task_business UNIQUE (tenant_id, task_type, business_key),
    CONSTRAINT ck_tsk_task_kind CHECK (task_kind IN ('AUTOMATED', 'HUMAN')),
    CONSTRAINT ck_tsk_task_status CHECK (
        status IN ('READY', 'PENDING', 'CLAIMED', 'RETRY_WAIT', 'SUCCEEDED', 'MANUAL_INTERVENTION', 'CANCELLED')
    ),
    CONSTRAINT ck_tsk_task_priority CHECK (priority BETWEEN 0 AND 1000),
    CONSTRAINT ck_tsk_task_attempts CHECK (
        attempt_count >= 0 AND max_attempts > 0 AND attempt_count <= max_attempts
    ),
    CONSTRAINT ck_tsk_task_claim CHECK (
        (status = 'CLAIMED' AND claim_owner IS NOT NULL AND claim_until IS NOT NULL AND current_attempt_id IS NOT NULL)
        OR
        (status <> 'CLAIMED' AND claim_owner IS NULL AND claim_until IS NULL AND current_attempt_id IS NULL)
    )
);

CREATE INDEX ix_tsk_task_due
    ON tsk_task (priority DESC, next_run_at, created_at, task_id)
    WHERE task_kind = 'AUTOMATED' AND status IN ('PENDING', 'RETRY_WAIT', 'CLAIMED');

CREATE TABLE tsk_task_execution_attempt (
    attempt_id      uuid         NOT NULL,
    task_id         uuid         NOT NULL,
    attempt_no      integer      NOT NULL,
    worker_id       varchar(128) NOT NULL,
    started_at      timestamptz  NOT NULL,
    finished_at     timestamptz,
    result_code     varchar(32)  NOT NULL,
    error_code      varchar(100),
    result_ref      varchar(500),
    next_retry_at   timestamptz,
    CONSTRAINT pk_tsk_task_execution_attempt PRIMARY KEY (attempt_id),
    CONSTRAINT fk_tsk_attempt_task FOREIGN KEY (task_id) REFERENCES tsk_task (task_id),
    CONSTRAINT uq_tsk_attempt_number UNIQUE (task_id, attempt_no),
    CONSTRAINT ck_tsk_attempt_no CHECK (attempt_no > 0),
    CONSTRAINT ck_tsk_attempt_result CHECK (
        result_code IN ('RUNNING', 'SUCCEEDED', 'RETRYABLE_FAILURE', 'FINAL_FAILURE', 'UNKNOWN', 'LEASE_EXPIRED')
    )
);

CREATE INDEX ix_tsk_attempt_task
    ON tsk_task_execution_attempt (task_id, attempt_no DESC);
