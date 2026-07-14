-- M47: CorrectionCase 关联整改 HUMAN Task；状态增加 IN_PROGRESS 投影。

ALTER TABLE evd_correction_case
    ADD COLUMN IF NOT EXISTS correction_task_id uuid;

ALTER TABLE evd_correction_case
    DROP CONSTRAINT IF EXISTS ck_evd_correction_case_status;

ALTER TABLE evd_correction_case
    ADD CONSTRAINT ck_evd_correction_case_status
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESUBMITTED', 'CLOSED'));

ALTER TABLE evd_correction_case
    DROP CONSTRAINT IF EXISTS ck_evd_correction_case_closed;

ALTER TABLE evd_correction_case
    ADD CONSTRAINT ck_evd_correction_case_closed CHECK (
        (status <> 'CLOSED' AND closed_at IS NULL AND closed_by IS NULL)
        OR (status = 'CLOSED' AND closed_at IS NOT NULL AND closed_by IS NOT NULL)
    );

CREATE UNIQUE INDEX IF NOT EXISTS uq_evd_correction_case_task
    ON evd_correction_case (tenant_id, correction_task_id)
    WHERE correction_task_id IS NOT NULL;

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
    IF OLD.status = 'CLOSED' THEN
        RAISE EXCEPTION 'closed correction case is immutable';
    END IF;
    IF OLD.status = 'OPEN' AND NEW.status NOT IN ('OPEN', 'IN_PROGRESS', 'RESUBMITTED') THEN
        RAISE EXCEPTION 'correction case status transition is invalid';
    END IF;
    IF OLD.status = 'IN_PROGRESS' AND NEW.status NOT IN ('IN_PROGRESS', 'RESUBMITTED') THEN
        RAISE EXCEPTION 'correction case status transition is invalid';
    END IF;
    IF OLD.status = 'RESUBMITTED' AND NEW.status NOT IN ('RESUBMITTED', 'CLOSED') THEN
        RAISE EXCEPTION 'correction case status transition is invalid';
    END IF;
    IF NEW.status = 'CLOSED' AND (NEW.closed_at IS NULL OR NEW.closed_by IS NULL) THEN
        RAISE EXCEPTION 'closed correction case requires closed_at and closed_by';
    END IF;
    RETURN NEW;
END;
$$;

COMMENT ON COLUMN evd_correction_case.correction_task_id IS 'M47：关联整改 HUMAN Task；责任与 SLA 只属于该 Task';
