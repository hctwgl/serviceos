-- M31 扩展预约终态，并为每次客户联系建立不可变事实链。
ALTER TABLE apt_appointment DROP CONSTRAINT ck_apt_appointment_status;
ALTER TABLE apt_appointment ADD CONSTRAINT ck_apt_appointment_status
    CHECK (status IN ('PROPOSED', 'CONFIRMED', 'CANCELLED', 'NO_SHOW'));

ALTER TABLE apt_appointment_status_history DROP CONSTRAINT ck_apt_history_status;
ALTER TABLE apt_appointment_status_history ADD CONSTRAINT ck_apt_history_status CHECK (
    (from_status IS NULL OR from_status IN ('PROPOSED', 'CONFIRMED', 'CANCELLED', 'NO_SHOW'))
    AND to_status IN ('PROPOSED', 'CONFIRMED', 'CANCELLED', 'NO_SHOW'));

-- 临时关闭不可变触发器，仅用于为 V030 历史修订补齐可判定的修订类型。
DROP TRIGGER trg_apt_revision_immutable ON apt_appointment_revision;
ALTER TABLE apt_appointment_revision
    ADD COLUMN revision_kind varchar(24),
    ADD COLUMN no_show_party_type varchar(80),
    ADD COLUMN no_show_party_ref varchar(200),
    ADD COLUMN no_show_evidence_refs jsonb NOT NULL DEFAULT '[]'::jsonb;
UPDATE apt_appointment_revision
   SET revision_kind = CASE
       WHEN reason_code IS NOT NULL THEN 'RESCHEDULE'
       WHEN confirmed_at IS NOT NULL THEN 'CONFIRM'
       ELSE 'PROPOSE'
   END;
ALTER TABLE apt_appointment_revision ALTER COLUMN revision_kind SET NOT NULL;
ALTER TABLE apt_appointment_revision ADD CONSTRAINT ck_apt_revision_kind
    CHECK (revision_kind IN ('PROPOSE', 'CONFIRM', 'RESCHEDULE', 'CANCEL', 'NO_SHOW'));
ALTER TABLE apt_appointment_revision ADD CONSTRAINT ck_apt_revision_terminal_facts CHECK (
    (revision_kind = 'CANCEL' AND reason_code IS NOT NULL
        AND no_show_party_type IS NULL AND no_show_party_ref IS NULL
        AND no_show_evidence_refs = '[]'::jsonb)
    OR (revision_kind = 'NO_SHOW' AND reason_code IS NOT NULL
        AND no_show_party_type IS NOT NULL AND no_show_party_ref IS NOT NULL
        AND jsonb_typeof(no_show_evidence_refs) = 'array')
    OR (revision_kind NOT IN ('CANCEL', 'NO_SHOW')
        AND no_show_party_type IS NULL AND no_show_party_ref IS NULL
        AND no_show_evidence_refs = '[]'::jsonb)
);
CREATE TRIGGER trg_apt_revision_immutable
    BEFORE UPDATE OR DELETE ON apt_appointment_revision
    FOR EACH ROW EXECUTE FUNCTION apt_reject_immutable_fact_mutation();

CREATE TABLE apt_contact_attempt (
    contact_attempt_id uuid NOT NULL,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    work_order_id uuid NOT NULL,
    task_id uuid NOT NULL,
    channel varchar(80) NOT NULL,
    contacted_party_ref varchar(200) NOT NULL,
    started_at timestamptz NOT NULL,
    ended_at timestamptz NOT NULL,
    result_code varchar(40) NOT NULL,
    note varchar(500),
    next_contact_at timestamptz,
    recording_ref varchar(500),
    actor_id varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_apt_contact_attempt PRIMARY KEY (contact_attempt_id),
    CONSTRAINT fk_apt_contact_attempt_task FOREIGN KEY (task_id) REFERENCES tsk_task (task_id),
    CONSTRAINT ck_apt_contact_attempt_time CHECK (ended_at >= started_at),
    CONSTRAINT ck_apt_contact_attempt_result CHECK (result_code IN (
        'CONNECTED', 'NO_ANSWER', 'BUSY', 'WRONG_NUMBER',
        'USER_REQUESTED_LATER', 'INVALID_CONTACT'))
);

CREATE TABLE apt_contact_attempt_command_result (
    tenant_id varchar(64) NOT NULL,
    operation_type varchar(100) NOT NULL,
    idempotency_key varchar(160) NOT NULL,
    contact_attempt_id uuid NOT NULL,
    CONSTRAINT pk_apt_contact_attempt_result
        PRIMARY KEY (tenant_id, operation_type, idempotency_key),
    CONSTRAINT fk_apt_contact_attempt_result FOREIGN KEY (contact_attempt_id)
        REFERENCES apt_contact_attempt (contact_attempt_id)
);

CREATE TRIGGER trg_apt_contact_attempt_immutable
    BEFORE UPDATE OR DELETE ON apt_contact_attempt
    FOR EACH ROW EXECUTE FUNCTION apt_reject_immutable_fact_mutation();

CREATE INDEX ix_apt_contact_attempt_task_time
    ON apt_contact_attempt (tenant_id, task_id, started_at, contact_attempt_id);

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES
    ('appointment.recordContact', '记录预约联系', 'NORMAL', now()),
    ('appointment.cancel', '取消预约', 'HIGH', now());
