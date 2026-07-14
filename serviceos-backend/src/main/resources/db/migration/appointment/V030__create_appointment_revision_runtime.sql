CREATE TABLE apt_appointment (
    appointment_id uuid NOT NULL,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    work_order_id uuid NOT NULL,
    task_id uuid NOT NULL,
    appointment_type varchar(32) NOT NULL,
    status varchar(24) NOT NULL,
    current_revision_id uuid NOT NULL,
    current_revision_no integer NOT NULL,
    assigned_network_id varchar(128),
    technician_id varchar(128),
    aggregate_version bigint NOT NULL,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_apt_appointment PRIMARY KEY (appointment_id),
    CONSTRAINT fk_apt_appointment_task FOREIGN KEY (task_id) REFERENCES tsk_task (task_id),
    CONSTRAINT ck_apt_appointment_type CHECK (
        appointment_type IN ('SURVEY', 'INSTALLATION', 'REPAIR', 'CORRECTION', 'SECOND_VISIT')),
    CONSTRAINT ck_apt_appointment_status CHECK (status IN ('PROPOSED', 'CONFIRMED')),
    CONSTRAINT ck_apt_appointment_version CHECK (
        aggregate_version > 0 AND current_revision_no > 0
        AND aggregate_version = current_revision_no)
);

CREATE TABLE apt_appointment_revision (
    revision_id uuid NOT NULL,
    tenant_id varchar(64) NOT NULL,
    appointment_id uuid NOT NULL,
    revision_no integer NOT NULL,
    previous_revision_id uuid,
    window_start timestamptz NOT NULL,
    window_end timestamptz NOT NULL,
    timezone varchar(80) NOT NULL,
    estimated_duration_minutes integer NOT NULL,
    address_ref varchar(200) NOT NULL,
    address_version varchar(100) NOT NULL,
    confirmed_party_type varchar(80),
    confirmed_party_ref varchar(200),
    confirmation_channel varchar(80),
    confirmed_at timestamptz,
    reason_code varchar(100),
    note varchar(500),
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_apt_appointment_revision PRIMARY KEY (revision_id),
    CONSTRAINT uq_apt_appointment_revision UNIQUE (tenant_id, appointment_id, revision_no),
    CONSTRAINT fk_apt_revision_appointment FOREIGN KEY (appointment_id)
        REFERENCES apt_appointment (appointment_id),
    CONSTRAINT fk_apt_revision_previous FOREIGN KEY (previous_revision_id)
        REFERENCES apt_appointment_revision (revision_id),
    CONSTRAINT ck_apt_revision_window CHECK (window_end > window_start),
    CONSTRAINT ck_apt_revision_duration CHECK (estimated_duration_minutes BETWEEN 1 AND 1440),
    CONSTRAINT ck_apt_revision_confirmation CHECK (
        (confirmed_party_type IS NULL AND confirmed_party_ref IS NULL
            AND confirmation_channel IS NULL AND confirmed_at IS NULL)
        OR
        (confirmed_party_type IS NOT NULL AND confirmed_party_ref IS NOT NULL
            AND confirmation_channel IS NOT NULL AND confirmed_at IS NOT NULL)
    ),
    CONSTRAINT ck_apt_revision_reason CHECK (
        reason_code IS NULL OR reason_code ~ '^[A-Z][A-Z0-9_]{1,99}$')
);

ALTER TABLE apt_appointment
    ADD CONSTRAINT fk_apt_appointment_current_revision
    FOREIGN KEY (current_revision_id) REFERENCES apt_appointment_revision (revision_id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE apt_appointment_status_history (
    history_id uuid NOT NULL,
    tenant_id varchar(64) NOT NULL,
    appointment_id uuid NOT NULL,
    aggregate_version bigint NOT NULL,
    from_status varchar(24),
    to_status varchar(24) NOT NULL,
    command_code varchar(80) NOT NULL,
    actor_id varchar(128) NOT NULL,
    reason_code varchar(100),
    revision_id uuid NOT NULL,
    occurred_at timestamptz NOT NULL,
    CONSTRAINT pk_apt_appointment_history PRIMARY KEY (history_id),
    CONSTRAINT uq_apt_appointment_history_version
        UNIQUE (tenant_id, appointment_id, aggregate_version),
    CONSTRAINT fk_apt_history_appointment FOREIGN KEY (appointment_id)
        REFERENCES apt_appointment (appointment_id),
    CONSTRAINT fk_apt_history_revision FOREIGN KEY (revision_id)
        REFERENCES apt_appointment_revision (revision_id),
    CONSTRAINT ck_apt_history_status CHECK (
        (from_status IS NULL OR from_status IN ('PROPOSED', 'CONFIRMED'))
        AND to_status IN ('PROPOSED', 'CONFIRMED')),
    CONSTRAINT ck_apt_history_reason CHECK (
        reason_code IS NULL OR reason_code ~ '^[A-Z][A-Z0-9_]{1,99}$')
);

CREATE TABLE apt_appointment_command_result (
    tenant_id varchar(64) NOT NULL,
    operation_type varchar(100) NOT NULL,
    idempotency_key varchar(160) NOT NULL,
    appointment_id uuid NOT NULL,
    revision_id uuid NOT NULL,
    status varchar(24) NOT NULL,
    revision_no integer NOT NULL,
    aggregate_version bigint NOT NULL,
    occurred_at timestamptz NOT NULL,
    CONSTRAINT pk_apt_appointment_command_result
        PRIMARY KEY (tenant_id, operation_type, idempotency_key),
    CONSTRAINT fk_apt_result_appointment FOREIGN KEY (appointment_id)
        REFERENCES apt_appointment (appointment_id),
    CONSTRAINT fk_apt_result_revision FOREIGN KEY (revision_id)
        REFERENCES apt_appointment_revision (revision_id)
);

-- 预约修订和状态历史是争议处理证据，只允许追加，禁止原地改写或删除。
CREATE OR REPLACE FUNCTION apt_reject_immutable_fact_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'appointment revision and status history are immutable';
END;
$$;

CREATE TRIGGER trg_apt_revision_immutable
    BEFORE UPDATE OR DELETE ON apt_appointment_revision
    FOR EACH ROW EXECUTE FUNCTION apt_reject_immutable_fact_mutation();

CREATE TRIGGER trg_apt_status_history_immutable
    BEFORE UPDATE OR DELETE ON apt_appointment_status_history
    FOR EACH ROW EXECUTE FUNCTION apt_reject_immutable_fact_mutation();

CREATE INDEX ix_apt_task_type_status
    ON apt_appointment (tenant_id, task_id, appointment_type, status, created_at, appointment_id);

CREATE INDEX ix_apt_technician_status
    ON apt_appointment (tenant_id, technician_id, status)
    WHERE technician_id IS NOT NULL;

CREATE INDEX ix_apt_revision_window
    ON apt_appointment_revision (tenant_id, window_start, window_end);

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES
    ('appointment.read', '查看预约', 'NORMAL', now()),
    ('appointment.propose', '提议预约', 'NORMAL', now()),
    ('appointment.manage', '确认和改约预约', 'HIGH', now());
