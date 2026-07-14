-- M42: EvidenceRevision 作废事实列；仅允许 VALIDATED → INVALIDATED 写一次。

ALTER TABLE evd_evidence_revision
    ADD COLUMN invalidated_by varchar(128),
    ADD COLUMN invalidated_at timestamptz,
    ADD COLUMN invalidation_reason_code varchar(80),
    ADD COLUMN invalidation_approval_ref varchar(160);

ALTER TABLE evd_evidence_revision
    ADD CONSTRAINT ck_evd_revision_invalidation_fields CHECK (
        (status = 'INVALIDATED'
            AND invalidated_by IS NOT NULL
            AND invalidated_at IS NOT NULL
            AND invalidation_reason_code IS NOT NULL
            AND length(btrim(invalidation_reason_code)) > 0)
        OR (status <> 'INVALIDATED'
            AND invalidated_by IS NULL
            AND invalidated_at IS NULL
            AND invalidation_reason_code IS NULL
            AND invalidation_approval_ref IS NULL)
    );

CREATE OR REPLACE FUNCTION evd_guard_revision_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'evidence revision is immutable';
    END IF;
    IF ROW(NEW.evidence_revision_id, NEW.tenant_id, NEW.project_id, NEW.task_id, NEW.slot_id,
           NEW.evidence_item_id, NEW.revision_number, NEW.file_object_id, NEW.content_digest,
           NEW.mime_type, NEW.size_bytes, NEW.capture_metadata, NEW.source_upload_session_id,
           NEW.finalize_command_id, NEW.created_by, NEW.created_at)
       IS DISTINCT FROM
       ROW(OLD.evidence_revision_id, OLD.tenant_id, OLD.project_id, OLD.task_id, OLD.slot_id,
           OLD.evidence_item_id, OLD.revision_number, OLD.file_object_id, OLD.content_digest,
           OLD.mime_type, OLD.size_bytes, OLD.capture_metadata, OLD.source_upload_session_id,
           OLD.finalize_command_id, OLD.created_by, OLD.created_at) THEN
        RAISE EXCEPTION 'evidence revision core facts are immutable';
    END IF;
    IF OLD.status = 'INVALIDATED' THEN
        IF ROW(NEW.status, NEW.invalidated_by, NEW.invalidated_at,
               NEW.invalidation_reason_code, NEW.invalidation_approval_ref)
           IS DISTINCT FROM
           ROW(OLD.status, OLD.invalidated_by, OLD.invalidated_at,
               OLD.invalidation_reason_code, OLD.invalidation_approval_ref) THEN
            RAISE EXCEPTION 'evidence revision invalidation facts are immutable';
        END IF;
    ELSIF NEW.status = 'INVALIDATED' THEN
        IF OLD.status <> 'VALIDATED' THEN
            RAISE EXCEPTION 'evidence revision can only invalidate from VALIDATED';
        END IF;
        IF NEW.invalidated_by IS NULL
           OR NEW.invalidated_at IS NULL
           OR NEW.invalidation_reason_code IS NULL
           OR length(btrim(NEW.invalidation_reason_code)) = 0 THEN
            RAISE EXCEPTION 'evidence revision invalidation requires actor, time and reason';
        END IF;
        IF OLD.invalidated_by IS NOT NULL
           OR OLD.invalidated_at IS NOT NULL
           OR OLD.invalidation_reason_code IS NOT NULL
           OR OLD.invalidation_approval_ref IS NOT NULL THEN
            RAISE EXCEPTION 'evidence revision invalidation metadata is write-once';
        END IF;
    ELSIF ROW(NEW.invalidated_by, NEW.invalidated_at,
              NEW.invalidation_reason_code, NEW.invalidation_approval_ref)
          IS DISTINCT FROM
          ROW(OLD.invalidated_by, OLD.invalidated_at,
              OLD.invalidation_reason_code, OLD.invalidation_approval_ref) THEN
        RAISE EXCEPTION 'evidence revision invalidation metadata requires INVALIDATED status';
    END IF;
    RETURN NEW;
END;
$$;

COMMENT ON COLUMN evd_evidence_revision.invalidation_reason_code IS
    'M42 作废原因码；仅 VALIDATED→INVALIDATED 写一次';
COMMENT ON COLUMN evd_evidence_revision.invalidation_approval_ref IS
    '可选审批引用；不改变文件模块状态，不改写历史 Snapshot';
