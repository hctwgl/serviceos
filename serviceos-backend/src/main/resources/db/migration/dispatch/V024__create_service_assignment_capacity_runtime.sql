CREATE TABLE dsp_capacity_counter (
    capacity_counter_id uuid NOT NULL,
    tenant_id varchar(64) NOT NULL,
    responsibility_level varchar(24) NOT NULL,
    assignee_id varchar(128) NOT NULL,
    business_type varchar(100) NOT NULL,
    max_units integer NOT NULL,
    occupied_units integer NOT NULL,
    version bigint NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_dsp_capacity_counter PRIMARY KEY (capacity_counter_id),
    CONSTRAINT uq_dsp_capacity_counter_key UNIQUE (
        tenant_id, responsibility_level, assignee_id, business_type),
    CONSTRAINT ck_dsp_capacity_counter_level CHECK (
        responsibility_level IN ('NETWORK', 'TECHNICIAN')),
    CONSTRAINT ck_dsp_capacity_counter_units CHECK (
        max_units > 0 AND occupied_units >= 0 AND occupied_units <= max_units),
    CONSTRAINT ck_dsp_capacity_counter_version CHECK (version > 0)
);

CREATE TABLE dsp_service_assignment (
    service_assignment_id uuid NOT NULL,
    tenant_id varchar(64) NOT NULL,
    work_order_id uuid NOT NULL,
    task_id uuid NOT NULL,
    responsibility_level varchar(24) NOT NULL,
    assignee_id varchar(128) NOT NULL,
    business_type varchar(100) NOT NULL,
    source_decision_id varchar(160) NOT NULL,
    status varchar(32) NOT NULL,
    activation_saga_id uuid NOT NULL,
    supersedes_service_assignment_id uuid,
    prepared_task_assignment_id uuid,
    task_execution_guard_id uuid,
    reassignment_reason_code varchar(100),
    effective_from timestamptz,
    effective_to timestamptz,
    authority_assignment_id varchar(160),
    authority_version bigint,
    fence_decision_id varchar(160),
    fence_policy_version varchar(160),
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    ended_by varchar(128),
    end_reason_code varchar(100),
    CONSTRAINT pk_dsp_service_assignment PRIMARY KEY (service_assignment_id),
    CONSTRAINT uq_dsp_assignment_saga UNIQUE (tenant_id, activation_saga_id),
    CONSTRAINT fk_dsp_assignment_supersedes FOREIGN KEY (supersedes_service_assignment_id)
        REFERENCES dsp_service_assignment (service_assignment_id),
    CONSTRAINT ck_dsp_assignment_level CHECK (
        responsibility_level IN ('NETWORK', 'TECHNICIAN')),
    CONSTRAINT ck_dsp_assignment_status CHECK (
        status IN ('PENDING_ACTIVATION', 'ACTIVE', 'ENDED', 'FAILED_ACTIVATION')),
    CONSTRAINT ck_dsp_assignment_reason CHECK (
        reassignment_reason_code IS NULL
        OR reassignment_reason_code ~ '^[A-Z][A-Z0-9_]{1,99}$'),
    CONSTRAINT ck_dsp_assignment_lifecycle CHECK (
        (status = 'PENDING_ACTIVATION'
            AND effective_from IS NULL AND effective_to IS NULL
            AND authority_assignment_id IS NULL AND authority_version IS NULL
            AND fence_decision_id IS NULL AND fence_policy_version IS NULL
            AND ended_by IS NULL AND end_reason_code IS NULL)
        OR
        (status = 'ACTIVE'
            AND effective_from IS NOT NULL AND effective_to IS NULL
            AND authority_assignment_id IS NOT NULL AND authority_version IS NOT NULL
            AND authority_version > 0
            AND fence_decision_id IS NOT NULL AND fence_policy_version IS NOT NULL
            AND ended_by IS NULL AND end_reason_code IS NULL)
        OR
        (status = 'ENDED'
            AND effective_from IS NOT NULL AND effective_to IS NOT NULL
            AND effective_to >= effective_from
            AND authority_assignment_id IS NOT NULL AND authority_version IS NOT NULL
            AND fence_decision_id IS NOT NULL AND fence_policy_version IS NOT NULL
            AND ended_by IS NOT NULL AND end_reason_code IS NOT NULL)
        OR
        (status = 'FAILED_ACTIVATION'
            AND effective_from IS NULL AND effective_to IS NULL
            AND authority_assignment_id IS NULL AND authority_version IS NULL
            AND fence_decision_id IS NULL AND fence_policy_version IS NULL
            AND ended_by IS NOT NULL AND end_reason_code IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_dsp_pending_assignment
    ON dsp_service_assignment (tenant_id, task_id, responsibility_level)
    WHERE status = 'PENDING_ACTIVATION';

CREATE UNIQUE INDEX uq_dsp_active_assignment
    ON dsp_service_assignment (tenant_id, task_id, responsibility_level)
    WHERE status = 'ACTIVE';

CREATE TABLE dsp_capacity_reservation (
    capacity_reservation_id uuid NOT NULL,
    tenant_id varchar(64) NOT NULL,
    service_assignment_id uuid NOT NULL,
    capacity_counter_id uuid NOT NULL,
    units integer NOT NULL,
    status varchar(24) NOT NULL,
    held_at timestamptz NOT NULL,
    confirmed_at timestamptz,
    released_at timestamptz,
    released_by varchar(128),
    release_reason_code varchar(100),
    CONSTRAINT pk_dsp_capacity_reservation PRIMARY KEY (capacity_reservation_id),
    CONSTRAINT uq_dsp_assignment_reservation UNIQUE (service_assignment_id),
    CONSTRAINT fk_dsp_reservation_assignment FOREIGN KEY (service_assignment_id)
        REFERENCES dsp_service_assignment (service_assignment_id),
    CONSTRAINT fk_dsp_reservation_counter FOREIGN KEY (capacity_counter_id)
        REFERENCES dsp_capacity_counter (capacity_counter_id),
    CONSTRAINT ck_dsp_reservation_units CHECK (units > 0),
    CONSTRAINT ck_dsp_reservation_status CHECK (status IN ('HELD', 'CONFIRMED', 'RELEASED')),
    CONSTRAINT ck_dsp_reservation_lifecycle CHECK (
        (status = 'HELD' AND confirmed_at IS NULL AND released_at IS NULL
            AND released_by IS NULL AND release_reason_code IS NULL)
        OR
        (status = 'CONFIRMED' AND confirmed_at IS NOT NULL AND released_at IS NULL
            AND released_by IS NULL AND release_reason_code IS NULL)
        OR
        (status = 'RELEASED' AND released_at IS NOT NULL
            AND released_by IS NOT NULL AND release_reason_code IS NOT NULL)
    )
);

CREATE TABLE dsp_service_assignment_activation_saga (
    activation_saga_id uuid NOT NULL,
    tenant_id varchar(64) NOT NULL,
    task_id uuid NOT NULL,
    new_service_assignment_id uuid NOT NULL,
    old_service_assignment_id uuid,
    stage varchar(32) NOT NULL,
    version bigint NOT NULL,
    prepared_task_assignment_id uuid,
    task_execution_guard_id uuid,
    last_error_code varchar(100),
    started_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    completed_at timestamptz,
    CONSTRAINT pk_dsp_assignment_saga PRIMARY KEY (activation_saga_id),
    CONSTRAINT uq_dsp_saga_new_assignment UNIQUE (new_service_assignment_id),
    CONSTRAINT fk_dsp_saga_new_assignment FOREIGN KEY (new_service_assignment_id)
        REFERENCES dsp_service_assignment (service_assignment_id),
    CONSTRAINT fk_dsp_saga_old_assignment FOREIGN KEY (old_service_assignment_id)
        REFERENCES dsp_service_assignment (service_assignment_id),
    CONSTRAINT ck_dsp_saga_stage CHECK (
        stage IN ('PENDING', 'TASK_PREPARED', 'SERVICE_SWITCHED',
                  'TASK_ACTIVATED', 'COMPLETED', 'ABORTING', 'ABORTED')),
    CONSTRAINT ck_dsp_saga_version CHECK (version > 0),
    CONSTRAINT ck_dsp_saga_completion CHECK (
        (stage = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (stage <> 'COMPLETED' AND completed_at IS NULL)
    )
);

CREATE TABLE dsp_assignment_command_result (
    tenant_id varchar(64) NOT NULL,
    operation_type varchar(100) NOT NULL,
    idempotency_key varchar(160) NOT NULL,
    service_assignment_id uuid NOT NULL,
    activation_saga_id uuid NOT NULL,
    task_id uuid NOT NULL,
    capacity_reservation_id uuid NOT NULL,
    assignment_status varchar(32) NOT NULL,
    saga_stage varchar(32) NOT NULL,
    saga_version bigint NOT NULL,
    occurred_at timestamptz NOT NULL,
    CONSTRAINT pk_dsp_assignment_command_result
        PRIMARY KEY (tenant_id, operation_type, idempotency_key),
    CONSTRAINT fk_dsp_result_assignment FOREIGN KEY (service_assignment_id)
        REFERENCES dsp_service_assignment (service_assignment_id),
    CONSTRAINT fk_dsp_result_saga FOREIGN KEY (activation_saga_id)
        REFERENCES dsp_service_assignment_activation_saga (activation_saga_id),
    CONSTRAINT fk_dsp_result_reservation FOREIGN KEY (capacity_reservation_id)
        REFERENCES dsp_capacity_reservation (capacity_reservation_id)
);

CREATE TABLE dsp_capacity_command_result (
    tenant_id varchar(64) NOT NULL,
    operation_type varchar(100) NOT NULL,
    idempotency_key varchar(160) NOT NULL,
    capacity_counter_id uuid NOT NULL,
    responsibility_level varchar(24) NOT NULL,
    assignee_id varchar(128) NOT NULL,
    business_type varchar(100) NOT NULL,
    max_units integer NOT NULL,
    occupied_units integer NOT NULL,
    counter_version bigint NOT NULL,
    occurred_at timestamptz NOT NULL,
    CONSTRAINT pk_dsp_capacity_command_result
        PRIMARY KEY (tenant_id, operation_type, idempotency_key),
    CONSTRAINT fk_dsp_capacity_result_counter FOREIGN KEY (capacity_counter_id)
        REFERENCES dsp_capacity_counter (capacity_counter_id)
);

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES
    ('dispatch.capacity.configure', '配置派单容量上限', 'HIGH', now()),
    ('dispatch.assignment.manage', '管理服务责任激活', 'HIGH', now());
