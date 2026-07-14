CREATE TABLE frm_form_submission (
    form_submission_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    task_id uuid NOT NULL REFERENCES tsk_task(task_id),
    project_id uuid NOT NULL,
    form_version_id uuid NOT NULL REFERENCES cfg_configuration_asset_version(version_id),
    form_key varchar(120) NOT NULL,
    submission_version integer NOT NULL CHECK (submission_version > 0),
    values_document jsonb NOT NULL CHECK (jsonb_typeof(values_document) = 'object'),
    content_digest char(64) NOT NULL CHECK (content_digest ~ '^[0-9a-f]{64}$'),
    validation_status varchar(20) NOT NULL CHECK (validation_status IN ('VALIDATED', 'INVALID')),
    prefill_version varchar(160),
    submitted_by varchar(128) NOT NULL,
    submitted_at timestamptz NOT NULL,
    supersedes_submission_id uuid REFERENCES frm_form_submission(form_submission_id),
    correction_reason varchar(500),
    CONSTRAINT uk_frm_submission_version UNIQUE (tenant_id, task_id, form_version_id, submission_version),
    CONSTRAINT ck_frm_correction_pair CHECK (
        (supersedes_submission_id IS NULL AND correction_reason IS NULL)
        OR (supersedes_submission_id IS NOT NULL AND correction_reason IS NOT NULL)
    )
);

CREATE INDEX ix_frm_submission_task ON frm_form_submission (tenant_id, task_id, submitted_at DESC);
CREATE INDEX ix_frm_submission_project ON frm_form_submission (tenant_id, project_id, submitted_at DESC);

CREATE TABLE frm_submission_validation (
    submission_validation_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    form_submission_id uuid NOT NULL UNIQUE REFERENCES frm_form_submission(form_submission_id),
    validator_version varchar(64) NOT NULL,
    input_digest char(64) NOT NULL CHECK (input_digest ~ '^[0-9a-f]{64}$'),
    validation_status varchar(20) NOT NULL CHECK (validation_status IN ('VALIDATED', 'INVALID')),
    errors_document jsonb NOT NULL CHECK (jsonb_typeof(errors_document) = 'array'),
    warnings_document jsonb NOT NULL CHECK (jsonb_typeof(warnings_document) = 'array'),
    executed_at timestamptz NOT NULL
);

CREATE TABLE frm_form_command_result (
    tenant_id varchar(64) NOT NULL,
    operation_type varchar(80) NOT NULL,
    idempotency_key varchar(160) NOT NULL,
    form_submission_id uuid NOT NULL REFERENCES frm_form_submission(form_submission_id),
    PRIMARY KEY (tenant_id, operation_type, idempotency_key)
);

CREATE OR REPLACE FUNCTION frm_reject_submission_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'form submissions and validations are immutable';
END;
$$;

CREATE TRIGGER trg_frm_submission_immutable
    BEFORE UPDATE OR DELETE ON frm_form_submission
    FOR EACH ROW EXECUTE FUNCTION frm_reject_submission_mutation();
CREATE TRIGGER trg_frm_validation_immutable
    BEFORE UPDATE OR DELETE ON frm_submission_validation
    FOR EACH ROW EXECUTE FUNCTION frm_reject_submission_mutation();

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('form.submit', '提交任务表单', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;

COMMENT ON TABLE frm_form_submission IS 'Task 锁定 FormVersion 的不可变提交版本；不等同审核通过';
COMMENT ON COLUMN frm_form_submission.values_document IS '原始扩展值文档；核心事实须经受控领域命令映射';
COMMENT ON TABLE frm_submission_validation IS '提交时权威服务端校验快照；解释器版本和输入摘要必须可追溯';
