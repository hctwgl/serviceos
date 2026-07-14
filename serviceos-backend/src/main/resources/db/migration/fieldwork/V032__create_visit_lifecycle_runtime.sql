-- M32：Visit 到场、离场和中断事实，以及项目级地理围栏策略。
ALTER TABLE apt_appointment DROP CONSTRAINT ck_apt_appointment_status;
ALTER TABLE apt_appointment ADD CONSTRAINT ck_apt_appointment_status CHECK (
    status IN ('PROPOSED', 'CONFIRMED', 'CANCELLED', 'NO_SHOW',
               'IN_PROGRESS', 'COMPLETED', 'INTERRUPTED'));
ALTER TABLE apt_appointment DROP CONSTRAINT ck_apt_appointment_version;
ALTER TABLE apt_appointment ADD CONSTRAINT ck_apt_appointment_version CHECK (
    aggregate_version > 0 AND current_revision_no > 0
    AND aggregate_version >= current_revision_no);

ALTER TABLE apt_appointment_status_history DROP CONSTRAINT ck_apt_history_status;
ALTER TABLE apt_appointment_status_history ADD CONSTRAINT ck_apt_history_status CHECK (
    (from_status IS NULL OR from_status IN (
        'PROPOSED', 'CONFIRMED', 'CANCELLED', 'NO_SHOW',
        'IN_PROGRESS', 'COMPLETED', 'INTERRUPTED'))
    AND to_status IN (
        'PROPOSED', 'CONFIRMED', 'CANCELLED', 'NO_SHOW',
        'IN_PROGRESS', 'COMPLETED', 'INTERRUPTED'));

CREATE TABLE fld_geofence_policy (
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    target_latitude numeric(9,6) NOT NULL,
    target_longitude numeric(9,6) NOT NULL,
    radius_meters numeric(10,2) NOT NULL,
    max_accuracy_meters numeric(10,2) NOT NULL,
    exception_action varchar(24) NOT NULL,
    policy_version varchar(80) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_fld_geofence_policy PRIMARY KEY (tenant_id, project_id),
    CONSTRAINT ck_fld_geofence_coordinates CHECK (
        target_latitude BETWEEN -90 AND 90 AND target_longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_fld_geofence_distance CHECK (
        radius_meters > 0 AND radius_meters <= 100000
        AND max_accuracy_meters > 0 AND max_accuracy_meters <= 10000),
    CONSTRAINT ck_fld_geofence_action CHECK (exception_action IN ('WARN', 'BLOCK'))
);

CREATE TABLE fld_visit (
    visit_id uuid NOT NULL,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    work_order_id uuid NOT NULL,
    task_id uuid NOT NULL,
    appointment_id uuid NOT NULL,
    visit_sequence integer NOT NULL,
    technician_id varchar(128) NOT NULL,
    network_id varchar(128),
    status varchar(24) NOT NULL,
    check_in_captured_at timestamptz NOT NULL,
    check_in_received_at timestamptz NOT NULL,
    check_in_latitude numeric(9,6) NOT NULL,
    check_in_longitude numeric(9,6) NOT NULL,
    check_in_accuracy_meters numeric(10,2) NOT NULL,
    geofence_result varchar(32) NOT NULL,
    geofence_distance_meters numeric(12,2),
    geofence_policy_version varchar(80),
    policy_decision varchar(24) NOT NULL,
    device_id varchar(160) NOT NULL,
    device_command_id varchar(160) NOT NULL,
    offline_flag boolean NOT NULL,
    check_out_captured_at timestamptz,
    check_out_received_at timestamptz,
    result_code varchar(100),
    exception_code varchar(100),
    note varchar(500),
    operation_refs jsonb NOT NULL DEFAULT '[]'::jsonb,
    evidence_refs jsonb NOT NULL DEFAULT '[]'::jsonb,
    aggregate_version bigint NOT NULL,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_fld_visit PRIMARY KEY (visit_id),
    CONSTRAINT uq_fld_visit_sequence UNIQUE (tenant_id, task_id, visit_sequence),
    CONSTRAINT uq_fld_visit_device_command UNIQUE (tenant_id, device_id, device_command_id),
    CONSTRAINT fk_fld_visit_task FOREIGN KEY (task_id) REFERENCES tsk_task (task_id),
    CONSTRAINT fk_fld_visit_appointment FOREIGN KEY (appointment_id)
        REFERENCES apt_appointment (appointment_id),
    CONSTRAINT ck_fld_visit_sequence CHECK (visit_sequence > 0),
    CONSTRAINT ck_fld_visit_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'INTERRUPTED')),
    CONSTRAINT ck_fld_visit_coordinates CHECK (
        check_in_latitude BETWEEN -90 AND 90 AND check_in_longitude BETWEEN -180 AND 180
        AND check_in_accuracy_meters > 0),
    CONSTRAINT ck_fld_visit_geofence CHECK (geofence_result IN (
        'WITHIN_GEOFENCE', 'OUTSIDE_GEOFENCE', 'LOCATION_UNAVAILABLE', 'LOW_ACCURACY')),
    CONSTRAINT ck_fld_visit_policy_decision CHECK (policy_decision IN ('ACCEPTED', 'WARNING')),
    CONSTRAINT ck_fld_visit_terminal CHECK (
        (status = 'IN_PROGRESS' AND check_out_captured_at IS NULL AND check_out_received_at IS NULL
            AND result_code IS NULL AND exception_code IS NULL)
        OR (status = 'COMPLETED' AND check_out_captured_at IS NOT NULL
            AND check_out_received_at IS NOT NULL AND result_code IS NOT NULL
            AND exception_code IS NULL AND jsonb_array_length(operation_refs) > 0)
        OR (status = 'INTERRUPTED' AND check_out_captured_at IS NOT NULL
            AND check_out_received_at IS NOT NULL AND exception_code IS NOT NULL)
    ),
    CONSTRAINT ck_fld_visit_version CHECK (aggregate_version > 0),
    CONSTRAINT ck_fld_visit_result_code CHECK (
        result_code IS NULL OR result_code ~ '^[A-Z][A-Z0-9_]{1,99}$'),
    CONSTRAINT ck_fld_visit_exception_code CHECK (
        exception_code IS NULL OR exception_code ~ '^[A-Z][A-Z0-9_]{1,99}$')
);

CREATE TABLE fld_visit_fact (
    visit_fact_id uuid NOT NULL,
    tenant_id varchar(64) NOT NULL,
    visit_id uuid NOT NULL,
    aggregate_version bigint NOT NULL,
    fact_type varchar(24) NOT NULL,
    captured_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL,
    latitude numeric(9,6),
    longitude numeric(9,6),
    accuracy_meters numeric(10,2),
    geofence_result varchar(32),
    result_code varchar(100),
    exception_code varchar(100),
    note varchar(500),
    reference_list jsonb NOT NULL DEFAULT '[]'::jsonb,
    actor_id varchar(128) NOT NULL,
    device_id varchar(160),
    offline_flag boolean NOT NULL,
    CONSTRAINT pk_fld_visit_fact PRIMARY KEY (visit_fact_id),
    CONSTRAINT uq_fld_visit_fact_version UNIQUE (tenant_id, visit_id, aggregate_version),
    CONSTRAINT fk_fld_visit_fact_visit FOREIGN KEY (visit_id) REFERENCES fld_visit (visit_id),
    CONSTRAINT ck_fld_visit_fact_type CHECK (fact_type IN ('CHECK_IN', 'CHECK_OUT', 'INTERRUPT')),
    CONSTRAINT ck_fld_visit_fact_location CHECK (
        (latitude IS NULL AND longitude IS NULL AND accuracy_meters IS NULL)
        OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180
            AND accuracy_meters > 0))
);

CREATE TABLE fld_visit_command_result (
    tenant_id varchar(64) NOT NULL,
    operation_type varchar(100) NOT NULL,
    idempotency_key varchar(160) NOT NULL,
    visit_id uuid NOT NULL,
    status varchar(24) NOT NULL,
    aggregate_version bigint NOT NULL,
    geofence_result varchar(32),
    policy_decision varchar(24),
    occurred_at timestamptz NOT NULL,
    CONSTRAINT pk_fld_visit_command_result
        PRIMARY KEY (tenant_id, operation_type, idempotency_key),
    CONSTRAINT fk_fld_visit_result FOREIGN KEY (visit_id) REFERENCES fld_visit (visit_id)
);

CREATE OR REPLACE FUNCTION fld_reject_visit_fact_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'visit fact is immutable';
END;
$$;
CREATE TRIGGER trg_fld_visit_fact_immutable
    BEFORE UPDATE OR DELETE ON fld_visit_fact
    FOR EACH ROW EXECUTE FUNCTION fld_reject_visit_fact_mutation();

-- Visit 聚合允许状态推进，但到场身份、时间和定位事实一经创建不得改写。
CREATE OR REPLACE FUNCTION fld_guard_visit_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.tenant_id IS DISTINCT FROM NEW.tenant_id
        OR OLD.project_id IS DISTINCT FROM NEW.project_id
        OR OLD.work_order_id IS DISTINCT FROM NEW.work_order_id
        OR OLD.task_id IS DISTINCT FROM NEW.task_id
        OR OLD.appointment_id IS DISTINCT FROM NEW.appointment_id
        OR OLD.visit_sequence IS DISTINCT FROM NEW.visit_sequence
        OR OLD.technician_id IS DISTINCT FROM NEW.technician_id
        OR OLD.network_id IS DISTINCT FROM NEW.network_id
        OR OLD.check_in_captured_at IS DISTINCT FROM NEW.check_in_captured_at
        OR OLD.check_in_received_at IS DISTINCT FROM NEW.check_in_received_at
        OR OLD.check_in_latitude IS DISTINCT FROM NEW.check_in_latitude
        OR OLD.check_in_longitude IS DISTINCT FROM NEW.check_in_longitude
        OR OLD.check_in_accuracy_meters IS DISTINCT FROM NEW.check_in_accuracy_meters
        OR OLD.geofence_result IS DISTINCT FROM NEW.geofence_result
        OR OLD.device_id IS DISTINCT FROM NEW.device_id
        OR OLD.device_command_id IS DISTINCT FROM NEW.device_command_id
        OR OLD.offline_flag IS DISTINCT FROM NEW.offline_flag
        OR NEW.aggregate_version <> OLD.aggregate_version + 1 THEN
        RAISE EXCEPTION 'visit check-in fact and identity are immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_fld_visit_guard
    BEFORE UPDATE ON fld_visit
    FOR EACH ROW EXECUTE FUNCTION fld_guard_visit_mutation();
CREATE TRIGGER trg_fld_visit_delete_immutable
    BEFORE DELETE ON fld_visit
    FOR EACH ROW EXECUTE FUNCTION fld_reject_visit_fact_mutation();

CREATE INDEX ix_fld_visit_work_order
    ON fld_visit (tenant_id, work_order_id, check_in_captured_at, visit_id);
CREATE INDEX ix_fld_visit_task_status ON fld_visit (tenant_id, task_id, status);

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES
    ('visit.read', '查看上门记录', 'NORMAL', now()),
    ('visit.checkIn', '现场签到', 'HIGH', now()),
    ('visit.checkOut', '现场签退', 'HIGH', now()),
    ('visit.interrupt', '中断现场作业', 'HIGH', now());
