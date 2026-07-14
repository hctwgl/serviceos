-- M49: ExternalReviewReceipt；ReviewDecision 增加 EXTERNAL 来源。

ALTER TABLE evd_review_decision
    ADD COLUMN IF NOT EXISTS decision_source varchar(32) NOT NULL DEFAULT 'INTERNAL';

ALTER TABLE evd_review_decision
    DROP CONSTRAINT IF EXISTS ck_evd_review_decision_source;

ALTER TABLE evd_review_decision
    ADD CONSTRAINT ck_evd_review_decision_source
        CHECK (decision_source IN ('INTERNAL', 'EXTERNAL'));

CREATE TABLE IF NOT EXISTS evd_external_review_receipt (
    receipt_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    review_case_id uuid NOT NULL,
    review_decision_id uuid NOT NULL,
    inbound_envelope_id varchar(160) NOT NULL,
    canonical_message_id varchar(160) NOT NULL,
    external_key varchar(160) NOT NULL,
    callback_batch_ref varchar(160) NOT NULL,
    mapping_version_id varchar(160) NOT NULL,
    result varchar(32) NOT NULL,
    reason_codes jsonb NOT NULL,
    affected_targets jsonb NOT NULL,
    payload_ref varchar(160),
    coordination_task_id uuid,
    received_by varchar(128) NOT NULL,
    received_at timestamptz NOT NULL,
    CONSTRAINT fk_evd_external_receipt_case FOREIGN KEY (review_case_id)
        REFERENCES evd_review_case(review_case_id),
    CONSTRAINT fk_evd_external_receipt_decision FOREIGN KEY (review_decision_id)
        REFERENCES evd_review_decision(review_decision_id),
    CONSTRAINT uq_evd_external_receipt_envelope UNIQUE (tenant_id, inbound_envelope_id),
    CONSTRAINT uq_evd_external_receipt_external_key UNIQUE (tenant_id, external_key),
    CONSTRAINT ck_evd_external_receipt_result CHECK (result IN ('APPROVED', 'REJECTED')),
    CONSTRAINT ck_evd_external_receipt_reasons CHECK (jsonb_typeof(reason_codes) = 'array'),
    CONSTRAINT ck_evd_external_receipt_targets CHECK (jsonb_typeof(affected_targets) = 'array')
);

CREATE INDEX IF NOT EXISTS ix_evd_external_receipt_case
    ON evd_external_review_receipt (tenant_id, review_case_id, received_at DESC);

CREATE TABLE IF NOT EXISTS evd_external_receipt_command_result (
    tenant_id varchar(64) NOT NULL,
    operation_type varchar(80) NOT NULL,
    idempotency_key varchar(160) NOT NULL,
    result_id uuid NOT NULL,
    PRIMARY KEY (tenant_id, operation_type, idempotency_key)
);

CREATE OR REPLACE FUNCTION evd_reject_external_receipt_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'external review receipt is immutable';
END;
$$;

DROP TRIGGER IF EXISTS trg_evd_external_receipt_immutable ON evd_external_review_receipt;
CREATE TRIGGER trg_evd_external_receipt_immutable
    BEFORE UPDATE OR DELETE ON evd_external_review_receipt
    FOR EACH ROW EXECUTE FUNCTION evd_reject_external_receipt_mutation();

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES
    ('evidence.recordExternalReceipt', '记录车企外部审核回执', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;

COMMENT ON TABLE evd_external_review_receipt IS 'M49：车企回执不可变记录；适配成功后追加 EXTERNAL ReviewDecision';
