ALTER TABLE tsk_task
    DROP CONSTRAINT ck_tsk_task_status,
    DROP CONSTRAINT ck_tsk_task_claim,
    ADD COLUMN claimed_by varchar(128),
    ADD COLUMN claimed_at timestamptz,
    ADD COLUMN started_at timestamptz,
    ADD COLUMN result_ref varchar(500),
    ADD COLUMN result_digest char(64),
    ADD CONSTRAINT ck_tsk_task_status CHECK (
        status IN ('READY', 'PENDING', 'CLAIMED', 'RUNNING', 'RETRY_WAIT', 'SUCCEEDED',
                   'COMPLETED', 'MANUAL_INTERVENTION', 'CANCELLED')
    ),
    ADD CONSTRAINT ck_tsk_automated_claim CHECK (
        (task_kind = 'AUTOMATED' AND (
            (status = 'CLAIMED' AND claim_owner IS NOT NULL
                AND claim_until IS NOT NULL AND current_attempt_id IS NOT NULL)
            OR
            (status <> 'CLAIMED' AND claim_owner IS NULL
                AND claim_until IS NULL AND current_attempt_id IS NULL)
        ))
        OR
        (task_kind = 'HUMAN' AND claim_owner IS NULL
            AND claim_until IS NULL AND current_attempt_id IS NULL)
    ),
    ADD CONSTRAINT ck_tsk_human_lifecycle CHECK (
        (task_kind = 'AUTOMATED' AND claimed_by IS NULL AND claimed_at IS NULL AND started_at IS NULL)
        OR
        (task_kind = 'HUMAN' AND (
            (status = 'READY' AND claimed_by IS NULL AND claimed_at IS NULL AND started_at IS NULL)
            OR
            (status = 'CLAIMED' AND claimed_by IS NOT NULL AND claimed_at IS NOT NULL
                AND started_at IS NULL)
            OR
            (status IN ('RUNNING', 'COMPLETED') AND claimed_by IS NOT NULL
                AND claimed_at IS NOT NULL AND started_at IS NOT NULL)
            OR
            (status IN ('MANUAL_INTERVENTION', 'CANCELLED'))
        ))
    ),
    ADD CONSTRAINT ck_tsk_result_digest CHECK (
        result_digest IS NULL OR result_digest ~ '^[0-9a-f]{64}$'
    ),
    ADD CONSTRAINT ck_tsk_human_completion CHECK (
        (task_kind = 'HUMAN' AND status = 'COMPLETED'
            AND completed_at IS NOT NULL AND result_ref IS NOT NULL AND result_digest IS NOT NULL)
        OR
        (task_kind <> 'HUMAN' OR status <> 'COMPLETED')
    );

CREATE INDEX ix_tsk_human_assignee
    ON tsk_task (tenant_id, claimed_by, status, priority DESC, next_run_at)
    WHERE task_kind = 'HUMAN' AND status IN ('CLAIMED', 'RUNNING');

-- 通用幂等表只保存资源引用；此表冻结首次命令响应，避免 claim 重放在任务已 start 后返回漂移状态。
CREATE TABLE tsk_human_task_command_result (
    tenant_id varchar(64) NOT NULL,
    operation_type varchar(100) NOT NULL,
    idempotency_key varchar(160) NOT NULL,
    task_id uuid NOT NULL,
    status varchar(32) NOT NULL,
    actor_id varchar(128) NOT NULL,
    task_version bigint NOT NULL,
    occurred_at timestamptz NOT NULL,
    CONSTRAINT pk_tsk_human_command_result
        PRIMARY KEY (tenant_id, operation_type, idempotency_key),
    CONSTRAINT fk_tsk_human_command_task FOREIGN KEY (task_id) REFERENCES tsk_task (task_id)
);

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES
    ('task.claim', '领取人工任务', 'NORMAL', now()),
    ('task.start', '启动人工任务', 'NORMAL', now()),
    ('task.complete', '完成人工任务', 'HIGH', now());
