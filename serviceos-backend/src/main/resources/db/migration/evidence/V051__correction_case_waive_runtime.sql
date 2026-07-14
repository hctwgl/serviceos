-- M51: CorrectionCase WAIVED 终态与高风险豁免能力。

ALTER TABLE evd_correction_case
    ADD COLUMN IF NOT EXISTS waived_by varchar(128),
    ADD COLUMN IF NOT EXISTS waived_at timestamptz,
    ADD COLUMN IF NOT EXISTS waive_approval_ref varchar(160),
    ADD COLUMN IF NOT EXISTS waive_note varchar(1000);

ALTER TABLE evd_correction_case
    DROP CONSTRAINT IF EXISTS ck_evd_correction_case_status;

ALTER TABLE evd_correction_case
    ADD CONSTRAINT ck_evd_correction_case_status
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESUBMITTED', 'CLOSED', 'WAIVED'));

ALTER TABLE evd_correction_case
    DROP CONSTRAINT IF EXISTS ck_evd_correction_case_closed;

ALTER TABLE evd_correction_case
    ADD CONSTRAINT ck_evd_correction_case_closed CHECK (
        (status NOT IN ('CLOSED', 'WAIVED')
            AND closed_at IS NULL AND closed_by IS NULL
            AND waived_at IS NULL AND waived_by IS NULL
            AND waive_approval_ref IS NULL)
        OR (status = 'CLOSED'
            AND closed_at IS NOT NULL AND closed_by IS NOT NULL
            AND waived_at IS NULL AND waived_by IS NULL
            AND waive_approval_ref IS NULL)
        OR (status = 'WAIVED'
            AND waived_at IS NOT NULL AND waived_by IS NOT NULL
            AND waive_approval_ref IS NOT NULL
            AND closed_at IS NULL AND closed_by IS NULL)
    );

CREATE OR REPLACE FUNCTION evd_guard_correction_case_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'correction case is immutable';
    END IF;
    IF ROW(NEW.correction_case_id, NEW.tenant_id, NEW.project_id, NEW.task_id,
           NEW.source_review_case_id, NEW.source_review_decision_id,
           NEW.source_evidence_set_snapshot_id, NEW.source_snapshot_content_digest,
           NEW.reason_codes, NEW.created_by, NEW.created_at)
       IS DISTINCT FROM
       ROW(OLD.correction_case_id, OLD.tenant_id, OLD.project_id, OLD.task_id,
           OLD.source_review_case_id, OLD.source_review_decision_id,
           OLD.source_evidence_set_snapshot_id, OLD.source_snapshot_content_digest,
           OLD.reason_codes, OLD.created_by, OLD.created_at) THEN
        RAISE EXCEPTION 'correction case identity is immutable';
    END IF;
    IF OLD.correction_task_id IS NOT NULL
       AND NEW.correction_task_id IS DISTINCT FROM OLD.correction_task_id THEN
        RAISE EXCEPTION 'correction task link is immutable';
    END IF;
    IF OLD.status IN ('CLOSED', 'WAIVED') THEN
        RAISE EXCEPTION 'terminal correction case is immutable';
    END IF;
    IF OLD.status = 'OPEN' AND NEW.status NOT IN ('OPEN', 'IN_PROGRESS', 'RESUBMITTED', 'WAIVED') THEN
        RAISE EXCEPTION 'correction case status transition is invalid';
    END IF;
    IF OLD.status = 'IN_PROGRESS' AND NEW.status NOT IN ('IN_PROGRESS', 'RESUBMITTED', 'WAIVED') THEN
        RAISE EXCEPTION 'correction case status transition is invalid';
    END IF;
    IF OLD.status = 'RESUBMITTED' AND NEW.status NOT IN ('RESUBMITTED', 'CLOSED', 'WAIVED') THEN
        RAISE EXCEPTION 'correction case status transition is invalid';
    END IF;
    IF NEW.status = 'CLOSED' AND (NEW.closed_at IS NULL OR NEW.closed_by IS NULL) THEN
        RAISE EXCEPTION 'closed correction case requires closed_at and closed_by';
    END IF;
    IF NEW.status = 'WAIVED' AND (
            NEW.waived_at IS NULL OR NEW.waived_by IS NULL OR NEW.waive_approval_ref IS NULL) THEN
        RAISE EXCEPTION 'waived correction case requires waived facts and approvalRef';
    END IF;
    RETURN NEW;
END;
$$;

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES
    ('evidence.waiveCorrection', '高风险豁免整改案例', 'CRITICAL', now())
ON CONFLICT (capability_code) DO NOTHING;

COMMENT ON COLUMN evd_correction_case.waive_approval_ref IS 'M51：WAIVED 必填授权依据';
