-- M55：总部审核通过并完成外部提交后，创建可追溯的 CLIENT ReviewCase。

ALTER TABLE evd_review_case
    ADD COLUMN origin varchar(16) NOT NULL DEFAULT 'INTERNAL',
    ADD COLUMN source_review_case_id uuid,
    ADD COLUMN external_submission_ref varchar(160),
    ADD COLUMN callback_batch_ref varchar(160),
    ADD COLUMN mapping_version_id varchar(160);

-- 默认值只用于将新系统当前结构迁移到唯一权威模型；运行时必须显式写入 origin。
ALTER TABLE evd_review_case ALTER COLUMN origin DROP DEFAULT;

ALTER TABLE evd_review_case
    ADD CONSTRAINT ck_evd_review_case_origin
        CHECK (origin IN ('INTERNAL', 'CLIENT')),
    ADD CONSTRAINT fk_evd_review_case_source
        FOREIGN KEY (source_review_case_id) REFERENCES evd_review_case(review_case_id),
    ADD CONSTRAINT ck_evd_review_case_client_lineage CHECK (
        (origin = 'INTERNAL'
            AND source_review_case_id IS NULL
            AND external_submission_ref IS NULL
            AND callback_batch_ref IS NULL
            AND mapping_version_id IS NULL)
        OR
        (origin = 'CLIENT'
            AND source_review_case_id IS NOT NULL
            AND external_submission_ref IS NOT NULL
            AND length(trim(external_submission_ref)) > 0
            AND callback_batch_ref IS NOT NULL
            AND length(trim(callback_batch_ref)) > 0
            AND mapping_version_id IS NOT NULL
            AND length(trim(mapping_version_id)) > 0)
    );

DROP INDEX IF EXISTS uq_evd_review_case_open_snapshot;

CREATE UNIQUE INDEX uq_evd_review_case_open_snapshot_origin
    ON evd_review_case (tenant_id, evidence_set_snapshot_id, origin)
    WHERE status = 'OPEN';

CREATE UNIQUE INDEX uq_evd_review_case_client_external_submission
    ON evd_review_case (tenant_id, external_submission_ref)
    WHERE origin = 'CLIENT';

CREATE INDEX ix_evd_review_case_source
    ON evd_review_case (tenant_id, source_review_case_id)
    WHERE source_review_case_id IS NOT NULL;

CREATE OR REPLACE FUNCTION evd_guard_review_case_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'review case is immutable';
    END IF;
    IF ROW(NEW.review_case_id, NEW.tenant_id, NEW.project_id, NEW.task_id,
           NEW.evidence_set_snapshot_id, NEW.snapshot_content_digest, NEW.scope_type,
           NEW.origin, NEW.policy_version, NEW.created_by, NEW.created_at,
           NEW.source_review_case_id, NEW.external_submission_ref,
           NEW.callback_batch_ref, NEW.mapping_version_id,
           NEW.reopened_from_review_case_id, NEW.reopen_trigger_ref)
       IS DISTINCT FROM
       ROW(OLD.review_case_id, OLD.tenant_id, OLD.project_id, OLD.task_id,
           OLD.evidence_set_snapshot_id, OLD.snapshot_content_digest, OLD.scope_type,
           OLD.origin, OLD.policy_version, OLD.created_by, OLD.created_at,
           OLD.source_review_case_id, OLD.external_submission_ref,
           OLD.callback_batch_ref, OLD.mapping_version_id,
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
VALUES ('evidence.createClientReviewCase', '登记车企提交并创建 CLIENT 审核案例', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;

COMMENT ON COLUMN evd_review_case.origin IS 'M55：INTERNAL 总部审核或 CLIENT 车企审核';
COMMENT ON COLUMN evd_review_case.source_review_case_id IS 'M55：CLIENT 案例关联的已通过 INTERNAL 案例';
COMMENT ON COLUMN evd_review_case.external_submission_ref IS 'M55：适配层确认已回传的车企提交唯一引用';
COMMENT ON COLUMN evd_review_case.callback_batch_ref IS 'M55：后续回执必须精确匹配的批次引用';
COMMENT ON COLUMN evd_review_case.mapping_version_id IS 'M55：外部提交及回执锁定的映射版本';
