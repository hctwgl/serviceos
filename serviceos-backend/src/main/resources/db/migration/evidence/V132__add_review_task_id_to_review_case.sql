-- M364：ReviewCase 关联独立审核 HUMAN Task（review_task_id）；源提交 Task 仍为 task_id。

ALTER TABLE evd_review_case
    ADD COLUMN IF NOT EXISTS review_task_id uuid;

CREATE UNIQUE INDEX IF NOT EXISTS uq_evd_review_case_review_task
    ON evd_review_case (tenant_id, review_task_id)
    WHERE review_task_id IS NOT NULL;

-- 在 V054 守卫基础上增加 review_task_id 一经绑定不可改。
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
    IF OLD.review_task_id IS NOT NULL
       AND NEW.review_task_id IS DISTINCT FROM OLD.review_task_id THEN
        RAISE EXCEPTION 'review task link is immutable';
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

COMMENT ON COLUMN evd_review_case.review_task_id IS
    'M364：独立审核 HUMAN Task；责任与 SLA 只属于该 Task；task_id 仍为源提交 Task';
