CREATE TABLE tsk_task_assignment_batch (
    assignment_batch_id uuid NOT NULL,
    tenant_id varchar(64) NOT NULL,
    task_id uuid NOT NULL,
    source_type varchar(32) NOT NULL,
    source_id varchar(160) NOT NULL,
    candidate_count integer NOT NULL,
    task_version bigint NOT NULL,
    assigned_by varchar(128) NOT NULL,
    assigned_at timestamptz NOT NULL,
    CONSTRAINT pk_tsk_assignment_batch PRIMARY KEY (assignment_batch_id),
    CONSTRAINT fk_tsk_assignment_batch_task FOREIGN KEY (task_id) REFERENCES tsk_task (task_id),
    CONSTRAINT ck_tsk_assignment_batch_source CHECK (source_type IN ('ASSIGNEE_POLICY', 'MANUAL')),
    CONSTRAINT ck_tsk_assignment_batch_count CHECK (candidate_count BETWEEN 1 AND 100)
);

CREATE TABLE tsk_task_assignment (
    task_assignment_id uuid NOT NULL,
    tenant_id varchar(64) NOT NULL,
    task_id uuid NOT NULL,
    assignment_batch_id uuid,
    assignment_kind varchar(24) NOT NULL,
    principal_type varchar(24) NOT NULL,
    principal_id varchar(128) NOT NULL,
    status varchar(24) NOT NULL,
    source_type varchar(32) NOT NULL,
    source_id varchar(160) NOT NULL,
    effective_from timestamptz NOT NULL,
    effective_to timestamptz,
    supersedes_task_assignment_id uuid,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    revoked_by varchar(128),
    revoke_reason_code varchar(100),
    CONSTRAINT pk_tsk_task_assignment PRIMARY KEY (task_assignment_id),
    CONSTRAINT fk_tsk_assignment_task FOREIGN KEY (task_id) REFERENCES tsk_task (task_id),
    CONSTRAINT fk_tsk_assignment_batch FOREIGN KEY (assignment_batch_id)
        REFERENCES tsk_task_assignment_batch (assignment_batch_id),
    CONSTRAINT fk_tsk_assignment_supersedes FOREIGN KEY (supersedes_task_assignment_id)
        REFERENCES tsk_task_assignment (task_assignment_id),
    CONSTRAINT ck_tsk_assignment_kind CHECK (assignment_kind IN ('CANDIDATE', 'RESPONSIBLE')),
    CONSTRAINT ck_tsk_assignment_principal CHECK (principal_type = 'USER'),
    CONSTRAINT ck_tsk_assignment_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    CONSTRAINT ck_tsk_assignment_period CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_tsk_assignment_revocation CHECK (
        (status = 'ACTIVE' AND effective_to IS NULL AND revoked_by IS NULL AND revoke_reason_code IS NULL)
        OR
        (status <> 'ACTIVE' AND effective_to IS NOT NULL AND revoked_by IS NOT NULL
            AND revoke_reason_code IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_tsk_active_candidate
    ON tsk_task_assignment (tenant_id, task_id, principal_type, principal_id)
    WHERE assignment_kind = 'CANDIDATE' AND status = 'ACTIVE';

CREATE UNIQUE INDEX uq_tsk_active_responsible
    ON tsk_task_assignment (tenant_id, task_id)
    WHERE assignment_kind = 'RESPONSIBLE' AND status = 'ACTIVE';

CREATE INDEX ix_tsk_candidate_principal
    ON tsk_task_assignment (tenant_id, principal_id, task_id)
    WHERE assignment_kind = 'CANDIDATE' AND status = 'ACTIVE';

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES
    ('task.assign', '配置人工任务候选人', 'HIGH', now()),
    ('task.release', '释放已领取人工任务', 'NORMAL', now());
