-- M353：ReviewCase 聚合版本 + 单项审核目标决定（只追加）。

ALTER TABLE evd_review_case
    ADD COLUMN IF NOT EXISTS aggregate_version bigint NOT NULL DEFAULT 1;

ALTER TABLE evd_review_case
    DROP CONSTRAINT IF EXISTS ck_evd_review_case_aggregate_version;

ALTER TABLE evd_review_case
    ADD CONSTRAINT ck_evd_review_case_aggregate_version CHECK (aggregate_version >= 1);

CREATE TABLE IF NOT EXISTS evd_review_target_decision (
    review_target_decision_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    review_case_id uuid NOT NULL,
    review_decision_id uuid NOT NULL,
    target_type varchar(40) NOT NULL,
    target_id uuid NOT NULL,
    target_version integer NOT NULL,
    decision varchar(32) NOT NULL,
    reason_codes jsonb NOT NULL,
    note varchar(1000),
    created_at timestamptz NOT NULL,
    CONSTRAINT fk_evd_review_target_decision_case FOREIGN KEY (review_case_id)
        REFERENCES evd_review_case(review_case_id),
    CONSTRAINT fk_evd_review_target_decision_decision FOREIGN KEY (review_decision_id)
        REFERENCES evd_review_decision(review_decision_id),
    CONSTRAINT uq_evd_review_target_decision UNIQUE (tenant_id, review_decision_id, target_type, target_id),
    CONSTRAINT ck_evd_review_target_decision_type CHECK (target_type = 'EvidenceRevision'),
    CONSTRAINT ck_evd_review_target_decision_decision CHECK (decision IN ('APPROVED', 'REJECTED')),
    CONSTRAINT ck_evd_review_target_decision_version CHECK (target_version > 0),
    CONSTRAINT ck_evd_review_target_decision_reasons CHECK (jsonb_typeof(reason_codes) = 'array')
);

CREATE INDEX IF NOT EXISTS ix_evd_review_target_decision_case
    ON evd_review_target_decision (tenant_id, review_case_id, review_decision_id);

CREATE OR REPLACE FUNCTION evd_reject_review_target_decision_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'review target decision is immutable';
END;
$$;

DROP TRIGGER IF EXISTS trg_evd_review_target_decision_immutable ON evd_review_target_decision;
CREATE TRIGGER trg_evd_review_target_decision_immutable
    BEFORE UPDATE OR DELETE ON evd_review_target_decision
    FOR EACH ROW EXECUTE FUNCTION evd_reject_review_target_decision_mutation();

COMMENT ON COLUMN evd_review_case.aggregate_version IS 'M353：If-Match 并发版本；decide 成功后递增';
COMMENT ON TABLE evd_review_target_decision IS 'M353：整组审核中每个 EvidenceRevision 的单项决定；只追加';
