-- M39：机器校验事实与 Revision 生命周期 VALIDATING → VALIDATED|VALIDATION_FAILED。
-- 校验结果 append-only；OCR/图像质量等未实现检查对 BLOCK 失败关闭。

CREATE TABLE evd_evidence_validation (
    validation_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    task_id uuid NOT NULL,
    slot_id uuid NOT NULL,
    evidence_item_id uuid NOT NULL,
    evidence_revision_id uuid NOT NULL,
    check_type varchar(40) NOT NULL,
    severity varchar(16) NOT NULL,
    result varchar(16) NOT NULL,
    reason_code varchar(80),
    message varchar(500),
    details jsonb NOT NULL,
    validator_name varchar(80) NOT NULL,
    validator_version varchar(40) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_evd_validation_check UNIQUE (tenant_id, evidence_revision_id, check_type),
    CONSTRAINT fk_evd_validation_revision FOREIGN KEY (evidence_revision_id)
        REFERENCES evd_evidence_revision(evidence_revision_id),
    CONSTRAINT fk_evd_validation_item FOREIGN KEY (evidence_item_id)
        REFERENCES evd_evidence_item(evidence_item_id),
    CONSTRAINT fk_evd_validation_slot FOREIGN KEY (slot_id)
        REFERENCES evd_evidence_slot(slot_id),
    CONSTRAINT fk_evd_validation_task_scope FOREIGN KEY (tenant_id, task_id, project_id)
        REFERENCES tsk_task(tenant_id, task_id, project_id),
    CONSTRAINT ck_evd_validation_severity CHECK (severity IN ('WARN', 'BLOCK')),
    CONSTRAINT ck_evd_validation_result CHECK (result IN ('PASSED', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_evd_validation_details CHECK (jsonb_typeof(details) = 'object')
);

CREATE INDEX ix_evd_validation_revision
    ON evd_evidence_validation (tenant_id, evidence_revision_id, created_at);

CREATE OR REPLACE FUNCTION evd_reject_validation_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'evidence validation is immutable';
END;
$$;

CREATE TRIGGER trg_evd_validation_immutable
    BEFORE UPDATE OR DELETE ON evd_evidence_validation
    FOR EACH ROW EXECUTE FUNCTION evd_reject_validation_mutation();

COMMENT ON TABLE evd_evidence_validation IS '机器校验事实；append-only；不表达审核决定';
COMMENT ON COLUMN evd_evidence_validation.check_type IS 'FORMAT/SIZE/CAPTURE_POLICY/DUPLICATE/HISTORICAL_IMAGE 或模板声明的检查类型';
COMMENT ON COLUMN evd_evidence_validation.result IS 'PASSED/FAILED/SKIPPED；仅 BLOCK+FAILED 使 Revision 进入 VALIDATION_FAILED';
