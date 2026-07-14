-- M44: ReviewCase 绑定 EvidenceSetSnapshot；只追加 ReviewDecision（APPROVED/REJECTED）。

CREATE TABLE evd_review_case (
    review_case_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    task_id uuid NOT NULL,
    evidence_set_snapshot_id uuid NOT NULL,
    snapshot_content_digest char(64) NOT NULL,
    scope_type varchar(40) NOT NULL,
    policy_version varchar(80) NOT NULL,
    status varchar(32) NOT NULL,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    decided_at timestamptz,
    CONSTRAINT fk_evd_review_case_snapshot FOREIGN KEY (evidence_set_snapshot_id)
        REFERENCES evd_evidence_set_snapshot(evidence_set_snapshot_id),
    CONSTRAINT fk_evd_review_case_task_scope FOREIGN KEY (tenant_id, task_id, project_id)
        REFERENCES tsk_task(tenant_id, task_id, project_id),
    CONSTRAINT uq_evd_review_case_snapshot UNIQUE (tenant_id, evidence_set_snapshot_id),
    CONSTRAINT ck_evd_review_case_scope CHECK (scope_type = 'EVIDENCE_SET_SNAPSHOT'),
    CONSTRAINT ck_evd_review_case_status CHECK (status IN ('OPEN', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_evd_review_case_digest CHECK (snapshot_content_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_evd_review_case_decided CHECK (
        (status = 'OPEN' AND decided_at IS NULL)
        OR (status IN ('APPROVED', 'REJECTED') AND decided_at IS NOT NULL)
    )
);

CREATE INDEX ix_evd_review_case_task_created
    ON evd_review_case (tenant_id, task_id, created_at DESC);
CREATE INDEX ix_evd_review_case_project_status
    ON evd_review_case (tenant_id, project_id, status, created_at DESC);

CREATE TABLE evd_review_decision (
    review_decision_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    review_case_id uuid NOT NULL,
    decision_ordinal integer NOT NULL,
    decision varchar(32) NOT NULL,
    reason_codes jsonb NOT NULL,
    note varchar(1000),
    decided_by varchar(128) NOT NULL,
    decided_at timestamptz NOT NULL,
    CONSTRAINT fk_evd_review_decision_case FOREIGN KEY (review_case_id)
        REFERENCES evd_review_case(review_case_id),
    CONSTRAINT uq_evd_review_decision_ordinal UNIQUE (tenant_id, review_case_id, decision_ordinal),
    CONSTRAINT ck_evd_review_decision_type CHECK (decision IN ('APPROVED', 'REJECTED')),
    CONSTRAINT ck_evd_review_decision_ordinal CHECK (decision_ordinal > 0),
    CONSTRAINT ck_evd_review_decision_reasons CHECK (jsonb_typeof(reason_codes) = 'array')
);

CREATE INDEX ix_evd_review_decision_case
    ON evd_review_decision (tenant_id, review_case_id, decision_ordinal);

CREATE TABLE evd_review_command_result (
    tenant_id varchar(64) NOT NULL,
    operation_type varchar(80) NOT NULL,
    idempotency_key varchar(160) NOT NULL,
    result_id uuid NOT NULL,
    PRIMARY KEY (tenant_id, operation_type, idempotency_key)
);

-- ReviewDecision 只追加；ReviewCase 仅允许 OPEN→APPROVED|REJECTED 与 decided_at 写一次。
CREATE OR REPLACE FUNCTION evd_reject_review_decision_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'review decision is immutable';
END;
$$;

CREATE TRIGGER trg_evd_review_decision_immutable
    BEFORE UPDATE OR DELETE ON evd_review_decision
    FOR EACH ROW EXECUTE FUNCTION evd_reject_review_decision_mutation();

CREATE OR REPLACE FUNCTION evd_guard_review_case_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'review case is immutable';
    END IF;
    IF ROW(NEW.review_case_id, NEW.tenant_id, NEW.project_id, NEW.task_id,
           NEW.evidence_set_snapshot_id, NEW.snapshot_content_digest, NEW.scope_type,
           NEW.policy_version, NEW.created_by, NEW.created_at)
       IS DISTINCT FROM
       ROW(OLD.review_case_id, OLD.tenant_id, OLD.project_id, OLD.task_id,
           OLD.evidence_set_snapshot_id, OLD.snapshot_content_digest, OLD.scope_type,
           OLD.policy_version, OLD.created_by, OLD.created_at) THEN
        RAISE EXCEPTION 'review case identity is immutable';
    END IF;
    IF OLD.status <> 'OPEN' THEN
        IF ROW(NEW.status, NEW.decided_at) IS DISTINCT FROM ROW(OLD.status, OLD.decided_at) THEN
            RAISE EXCEPTION 'review case decision facts are immutable';
        END IF;
    ELSIF NEW.status NOT IN ('OPEN', 'APPROVED', 'REJECTED') THEN
        RAISE EXCEPTION 'review case status transition is invalid';
    ELSIF NEW.status <> 'OPEN' AND NEW.decided_at IS NULL THEN
        RAISE EXCEPTION 'review case decision requires decided_at';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_evd_review_case_guard
    BEFORE UPDATE OR DELETE ON evd_review_case
    FOR EACH ROW EXECUTE FUNCTION evd_guard_review_case_mutation();

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES
    ('evidence.review', '创建并裁决资料集合审核案例', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;

COMMENT ON TABLE evd_review_case IS 'M44：绑定单个 EvidenceSetSnapshot 的审核案例；当前状态为投影';
COMMENT ON TABLE evd_review_decision IS 'M44：只追加审核决定；更正须新案例，不改旧决定';
