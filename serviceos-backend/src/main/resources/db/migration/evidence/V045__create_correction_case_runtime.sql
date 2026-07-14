-- M45: CorrectionCase 由 REJECTED ReviewDecision 触发；补传轮次只追加。

CREATE TABLE evd_correction_case (
    correction_case_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    task_id uuid NOT NULL,
    source_review_case_id uuid NOT NULL,
    source_review_decision_id uuid NOT NULL,
    source_evidence_set_snapshot_id uuid NOT NULL,
    source_snapshot_content_digest char(64) NOT NULL,
    reason_codes jsonb NOT NULL,
    status varchar(32) NOT NULL,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    latest_resubmission_snapshot_id uuid,
    closed_by varchar(128),
    closed_at timestamptz,
    CONSTRAINT fk_evd_correction_case_review FOREIGN KEY (source_review_case_id)
        REFERENCES evd_review_case(review_case_id),
    CONSTRAINT fk_evd_correction_case_decision FOREIGN KEY (source_review_decision_id)
        REFERENCES evd_review_decision(review_decision_id),
    CONSTRAINT fk_evd_correction_case_snapshot FOREIGN KEY (source_evidence_set_snapshot_id)
        REFERENCES evd_evidence_set_snapshot(evidence_set_snapshot_id),
    CONSTRAINT fk_evd_correction_case_task_scope FOREIGN KEY (tenant_id, task_id, project_id)
        REFERENCES tsk_task(tenant_id, task_id, project_id),
    CONSTRAINT uq_evd_correction_case_decision UNIQUE (tenant_id, source_review_decision_id),
    CONSTRAINT ck_evd_correction_case_status CHECK (status IN ('OPEN', 'RESUBMITTED', 'CLOSED')),
    CONSTRAINT ck_evd_correction_case_digest CHECK (source_snapshot_content_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_evd_correction_case_reasons CHECK (jsonb_typeof(reason_codes) = 'array'),
    CONSTRAINT ck_evd_correction_case_closed CHECK (
        (status <> 'CLOSED' AND closed_at IS NULL AND closed_by IS NULL)
        OR (status = 'CLOSED' AND closed_at IS NOT NULL AND closed_by IS NOT NULL)
    )
);

CREATE INDEX ix_evd_correction_case_review
    ON evd_correction_case (tenant_id, source_review_case_id, created_at DESC);
CREATE INDEX ix_evd_correction_case_project_status
    ON evd_correction_case (tenant_id, project_id, status, created_at DESC);

CREATE TABLE evd_correction_resubmission (
    correction_resubmission_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    correction_case_id uuid NOT NULL,
    resubmission_ordinal integer NOT NULL,
    evidence_set_snapshot_id uuid NOT NULL,
    snapshot_content_digest char(64) NOT NULL,
    submitted_by varchar(128) NOT NULL,
    submitted_at timestamptz NOT NULL,
    CONSTRAINT fk_evd_correction_resubmission_case FOREIGN KEY (correction_case_id)
        REFERENCES evd_correction_case(correction_case_id),
    CONSTRAINT fk_evd_correction_resubmission_snapshot FOREIGN KEY (evidence_set_snapshot_id)
        REFERENCES evd_evidence_set_snapshot(evidence_set_snapshot_id),
    CONSTRAINT uq_evd_correction_resubmission_ordinal UNIQUE (tenant_id, correction_case_id, resubmission_ordinal),
    CONSTRAINT ck_evd_correction_resubmission_ordinal CHECK (resubmission_ordinal > 0),
    CONSTRAINT ck_evd_correction_resubmission_digest CHECK (snapshot_content_digest ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_evd_correction_resubmission_case
    ON evd_correction_resubmission (tenant_id, correction_case_id, resubmission_ordinal);

CREATE TABLE evd_correction_command_result (
    tenant_id varchar(64) NOT NULL,
    operation_type varchar(80) NOT NULL,
    idempotency_key varchar(160) NOT NULL,
    result_id uuid NOT NULL,
    PRIMARY KEY (tenant_id, operation_type, idempotency_key)
);

CREATE OR REPLACE FUNCTION evd_reject_correction_resubmission_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'correction resubmission is immutable';
END;
$$;

CREATE TRIGGER trg_evd_correction_resubmission_immutable
    BEFORE UPDATE OR DELETE ON evd_correction_resubmission
    FOR EACH ROW EXECUTE FUNCTION evd_reject_correction_resubmission_mutation();

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
    IF OLD.status = 'CLOSED' THEN
        RAISE EXCEPTION 'closed correction case is immutable';
    END IF;
    IF OLD.status = 'OPEN' AND NEW.status NOT IN ('OPEN', 'RESUBMITTED') THEN
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

CREATE TRIGGER trg_evd_correction_case_guard
    BEFORE UPDATE OR DELETE ON evd_correction_case
    FOR EACH ROW EXECUTE FUNCTION evd_guard_correction_case_mutation();

COMMENT ON TABLE evd_correction_case IS 'M45：由 REJECTED ReviewDecision 触发的整改案例；关闭不等于审核通过';
COMMENT ON TABLE evd_correction_resubmission IS 'M45：只追加的整改补传轮次';
