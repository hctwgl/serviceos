ALTER TABLE tsk_task_assignment
    DROP CONSTRAINT ck_tsk_assignment_status,
    DROP CONSTRAINT ck_tsk_assignment_period,
    DROP CONSTRAINT ck_tsk_assignment_revocation,
    ALTER COLUMN effective_from DROP NOT NULL,
    ADD COLUMN task_execution_guard_id uuid,
    ADD COLUMN preparation_key varchar(160),
    ADD COLUMN activation_ref varchar(160),
    ADD CONSTRAINT fk_tsk_assignment_execution_guard
        FOREIGN KEY (task_execution_guard_id)
        REFERENCES tsk_task_execution_guard (task_execution_guard_id),
    ADD CONSTRAINT ck_tsk_assignment_status CHECK (
        status IN ('PREPARED', 'ACTIVE', 'REVOKED', 'EXPIRED', 'ABORTED')
    ),
    ADD CONSTRAINT ck_tsk_assignment_lifecycle CHECK (
        (status = 'PREPARED'
            AND assignment_kind = 'RESPONSIBLE'
            AND task_execution_guard_id IS NOT NULL
            AND preparation_key IS NOT NULL
            AND activation_ref IS NULL
            AND effective_from IS NULL AND effective_to IS NULL
            AND revoked_by IS NULL AND revoke_reason_code IS NULL)
        OR
        (status = 'ACTIVE'
            AND effective_from IS NOT NULL AND effective_to IS NULL
            AND revoked_by IS NULL AND revoke_reason_code IS NULL
            AND (preparation_key IS NULL OR activation_ref IS NOT NULL))
        OR
        (status IN ('REVOKED', 'EXPIRED')
            AND effective_from IS NOT NULL AND effective_to IS NOT NULL
            AND effective_to >= effective_from
            AND revoked_by IS NOT NULL AND revoke_reason_code IS NOT NULL)
        OR
        (status = 'ABORTED'
            AND assignment_kind = 'RESPONSIBLE'
            AND task_execution_guard_id IS NOT NULL
            AND preparation_key IS NOT NULL
            AND activation_ref IS NULL
            AND effective_from IS NULL AND effective_to IS NULL
            AND revoked_by IS NOT NULL AND revoke_reason_code IS NOT NULL)
    );

CREATE UNIQUE INDEX uq_tsk_assignment_preparation_key
    ON tsk_task_assignment (tenant_id, preparation_key)
    WHERE preparation_key IS NOT NULL;

CREATE UNIQUE INDEX uq_tsk_prepared_responsible
    ON tsk_task_assignment (tenant_id, task_id)
    WHERE assignment_kind = 'RESPONSIBLE' AND status = 'PREPARED';

CREATE TABLE tsk_task_reassignment_command_result (
    tenant_id varchar(64) NOT NULL,
    operation_type varchar(100) NOT NULL,
    idempotency_key varchar(160) NOT NULL,
    task_id uuid NOT NULL,
    task_execution_guard_id uuid NOT NULL,
    prepared_task_assignment_id uuid NOT NULL,
    principal_id varchar(128) NOT NULL,
    status varchar(24) NOT NULL,
    task_version bigint NOT NULL,
    occurred_at timestamptz NOT NULL,
    CONSTRAINT pk_tsk_reassignment_command_result
        PRIMARY KEY (tenant_id, operation_type, idempotency_key),
    CONSTRAINT fk_tsk_reassignment_result_task FOREIGN KEY (task_id) REFERENCES tsk_task (task_id),
    CONSTRAINT fk_tsk_reassignment_result_guard FOREIGN KEY (task_execution_guard_id)
        REFERENCES tsk_task_execution_guard (task_execution_guard_id),
    CONSTRAINT fk_tsk_reassignment_result_assignment FOREIGN KEY (prepared_task_assignment_id)
        REFERENCES tsk_task_assignment (task_assignment_id),
    CONSTRAINT ck_tsk_reassignment_result_status CHECK (status IN ('PREPARED', 'ACTIVE', 'ABORTED'))
);

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('task.reassignment.manage', '管理任务责任切换握手', 'HIGH', now());
