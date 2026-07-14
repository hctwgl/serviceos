-- M48: FORCE_APPROVED / REOPENED；同 Snapshot 至多一个 OPEN；强制通过与重开能力。

ALTER TABLE evd_review_case
    ADD COLUMN IF NOT EXISTS reopened_from_review_case_id uuid,
    ADD COLUMN IF NOT EXISTS reopen_trigger_ref varchar(160);

ALTER TABLE evd_review_case
    DROP CONSTRAINT IF EXISTS uq_evd_review_case_snapshot;

ALTER TABLE evd_review_case
    DROP CONSTRAINT IF EXISTS ck_evd_review_case_status;

ALTER TABLE evd_review_case
    ADD CONSTRAINT ck_evd_review_case_status
        CHECK (status IN ('OPEN', 'APPROVED', 'REJECTED', 'FORCE_APPROVED', 'REOPENED'));

ALTER TABLE evd_review_case
    DROP CONSTRAINT IF EXISTS ck_evd_review_case_decided;

ALTER TABLE evd_review_case
    ADD CONSTRAINT ck_evd_review_case_decided CHECK (
        (status = 'OPEN' AND decided_at IS NULL)
        OR (status IN ('APPROVED', 'REJECTED', 'FORCE_APPROVED', 'REOPENED') AND decided_at IS NOT NULL)
    );

ALTER TABLE evd_review_case
    DROP CONSTRAINT IF EXISTS fk_evd_review_case_reopened_from;

ALTER TABLE evd_review_case
    ADD CONSTRAINT fk_evd_review_case_reopened_from
        FOREIGN KEY (reopened_from_review_case_id) REFERENCES evd_review_case(review_case_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_evd_review_case_open_snapshot
    ON evd_review_case (tenant_id, evidence_set_snapshot_id)
    WHERE status = 'OPEN';

ALTER TABLE evd_review_decision
    ADD COLUMN IF NOT EXISTS approval_ref varchar(160);

ALTER TABLE evd_review_decision
    DROP CONSTRAINT IF EXISTS ck_evd_review_decision_type;

ALTER TABLE evd_review_decision
    ADD CONSTRAINT ck_evd_review_decision_type
        CHECK (decision IN ('APPROVED', 'REJECTED', 'FORCE_APPROVED'));

ALTER TABLE evd_review_decision
    DROP CONSTRAINT IF EXISTS ck_evd_review_decision_force_approval;

ALTER TABLE evd_review_decision
    ADD CONSTRAINT ck_evd_review_decision_force_approval CHECK (
        (decision = 'FORCE_APPROVED' AND approval_ref IS NOT NULL AND length(trim(approval_ref)) > 0)
        OR (decision <> 'FORCE_APPROVED' AND approval_ref IS NULL)
    );

CREATE OR REPLACE FUNCTION evd_guard_review_case_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'review case is immutable';
    END IF;
    IF ROW(NEW.review_case_id, NEW.tenant_id, NEW.project_id, NEW.task_id,
           NEW.evidence_set_snapshot_id, NEW.snapshot_content_digest, NEW.scope_type,
           NEW.policy_version, NEW.created_by, NEW.created_at,
           NEW.reopened_from_review_case_id, NEW.reopen_trigger_ref)
       IS DISTINCT FROM
       ROW(OLD.review_case_id, OLD.tenant_id, OLD.project_id, OLD.task_id,
           OLD.evidence_set_snapshot_id, OLD.snapshot_content_digest, OLD.scope_type,
           OLD.policy_version, OLD.created_by, OLD.created_at,
           OLD.reopened_from_review_case_id, OLD.reopen_trigger_ref) THEN
        RAISE EXCEPTION 'review case identity is immutable';
    END IF;
    IF OLD.status = 'OPEN' THEN
        IF NEW.status NOT IN ('OPEN', 'APPROVED', 'REJECTED', 'FORCE_APPROVED') THEN
            RAISE EXCEPTION 'review case status transition is invalid';
        ELSIF NEW.status <> 'OPEN' AND NEW.decided_at IS NULL THEN
            RAISE EXCEPTION 'review case decision requires decided_at';
        END IF;
    ELSIF OLD.status IN ('APPROVED', 'FORCE_APPROVED') AND NEW.status = 'REOPENED' THEN
        IF NEW.decided_at IS DISTINCT FROM OLD.decided_at THEN
            RAISE EXCEPTION 'review case decided_at is immutable on reopen';
        END IF;
    ELSIF ROW(NEW.status, NEW.decided_at) IS DISTINCT FROM ROW(OLD.status, OLD.decided_at) THEN
        RAISE EXCEPTION 'review case decision facts are immutable';
    END IF;
    RETURN NEW;
END;
$$;

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES
    ('evidence.forceApprove', '资料审核强制通过', 'CRITICAL', now()),
    ('review.reopen', '重开已通过审核案例', 'CRITICAL', now())
ON CONFLICT (capability_code) DO NOTHING;

COMMENT ON COLUMN evd_review_case.reopened_from_review_case_id IS 'M48：重开产生的新案例追溯来源';
COMMENT ON COLUMN evd_review_decision.approval_ref IS 'M48：FORCE_APPROVED 必填授权依据';
