CREATE TABLE tsk_task_execution_guard (
    task_execution_guard_id uuid NOT NULL,
    tenant_id varchar(64) NOT NULL,
    task_id uuid NOT NULL,
    guard_type varchar(32) NOT NULL,
    guard_key varchar(160) NOT NULL,
    reason_code varchar(100) NOT NULL,
    status varchar(24) NOT NULL,
    activated_task_version bigint NOT NULL,
    activated_by varchar(128) NOT NULL,
    activated_at timestamptz NOT NULL,
    released_task_version bigint,
    released_by varchar(128),
    released_at timestamptz,
    release_reason_code varchar(100),
    CONSTRAINT pk_tsk_task_execution_guard PRIMARY KEY (task_execution_guard_id),
    CONSTRAINT fk_tsk_execution_guard_task FOREIGN KEY (task_id) REFERENCES tsk_task (task_id),
    CONSTRAINT uq_tsk_execution_guard_key UNIQUE (tenant_id, guard_type, guard_key),
    CONSTRAINT ck_tsk_execution_guard_type CHECK (guard_type = 'REASSIGNMENT'),
    CONSTRAINT ck_tsk_execution_guard_status CHECK (status IN ('ACTIVE', 'RELEASED')),
    CONSTRAINT ck_tsk_execution_guard_reason CHECK (
        reason_code ~ '^[A-Z][A-Z0-9_]{1,99}$'
        AND (release_reason_code IS NULL OR release_reason_code ~ '^[A-Z][A-Z0-9_]{1,99}$')
    ),
    CONSTRAINT ck_tsk_execution_guard_release CHECK (
        (status = 'ACTIVE' AND released_task_version IS NULL AND released_by IS NULL
            AND released_at IS NULL AND release_reason_code IS NULL)
        OR
        (status = 'RELEASED' AND released_task_version IS NOT NULL
            AND released_task_version > activated_task_version
            AND released_by IS NOT NULL AND released_at IS NOT NULL
            AND release_reason_code IS NOT NULL AND released_at >= activated_at)
    )
);

CREATE UNIQUE INDEX uq_tsk_active_execution_guard
    ON tsk_task_execution_guard (tenant_id, task_id)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_tsk_execution_guard_task_history
    ON tsk_task_execution_guard (tenant_id, task_id, activated_at DESC);

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('task.guard.manage', '管理任务执行保护窗', 'HIGH', now());
