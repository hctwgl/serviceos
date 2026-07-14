-- M38：EvidenceItem / 不可变 EvidenceRevision 与上传会话绑定。
-- Finalize 成功后才存在 Revision；核心文件事实不可覆盖，仅允许 lifecycle_status 受控迁移。

CREATE TABLE evd_evidence_upload_session (
    upload_session_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    task_id uuid NOT NULL,
    slot_id uuid NOT NULL,
    file_id uuid NOT NULL,
    evidence_item_id uuid,
    expected_sha256 char(64) NOT NULL,
    declared_mime_type varchar(120) NOT NULL,
    expected_size_bytes bigint NOT NULL,
    original_file_name varchar(255) NOT NULL,
    capture_metadata jsonb NOT NULL,
    status varchar(24) NOT NULL,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_evd_upload_file UNIQUE (tenant_id, file_id),
    CONSTRAINT fk_evd_upload_slot FOREIGN KEY (slot_id)
        REFERENCES evd_evidence_slot(slot_id),
    CONSTRAINT fk_evd_upload_task_scope FOREIGN KEY (tenant_id, task_id, project_id)
        REFERENCES tsk_task(tenant_id, task_id, project_id),
    CONSTRAINT fk_evd_upload_session_file FOREIGN KEY (upload_session_id)
        REFERENCES fil_upload_session(upload_session_id),
    CONSTRAINT fk_evd_upload_stored_file FOREIGN KEY (file_id)
        REFERENCES fil_stored_file(file_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_evd_upload_digest CHECK (expected_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_evd_upload_size CHECK (expected_size_bytes > 0),
    CONSTRAINT ck_evd_upload_status CHECK (status IN ('PENDING', 'FINALIZED')),
    CONSTRAINT ck_evd_upload_metadata CHECK (jsonb_typeof(capture_metadata) = 'object')
);

-- Begin 时 StoredFile 尚不存在；去掉对 fil_stored_file 的立即外键，仅保留 upload_session 引用。
ALTER TABLE evd_evidence_upload_session
    DROP CONSTRAINT fk_evd_upload_stored_file;

CREATE INDEX ix_evd_upload_slot_pending
    ON evd_evidence_upload_session (tenant_id, slot_id, status, created_at DESC);

CREATE TABLE evd_evidence_item (
    evidence_item_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    task_id uuid NOT NULL,
    slot_id uuid NOT NULL,
    item_ordinal integer NOT NULL,
    status varchar(24) NOT NULL,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_evd_item_ordinal UNIQUE (tenant_id, slot_id, item_ordinal),
    CONSTRAINT fk_evd_item_slot FOREIGN KEY (slot_id)
        REFERENCES evd_evidence_slot(slot_id),
    CONSTRAINT fk_evd_item_task_scope FOREIGN KEY (tenant_id, task_id, project_id)
        REFERENCES tsk_task(tenant_id, task_id, project_id),
    CONSTRAINT ck_evd_item_ordinal CHECK (item_ordinal > 0),
    CONSTRAINT ck_evd_item_status CHECK (status IN ('OPEN', 'SUBMITTED', 'UNDER_REVIEW', 'ACCEPTED', 'REJECTED', 'LOCKED'))
);

CREATE INDEX ix_evd_item_task ON evd_evidence_item (tenant_id, task_id, slot_id, item_ordinal);

CREATE TABLE evd_evidence_revision (
    evidence_revision_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    task_id uuid NOT NULL,
    slot_id uuid NOT NULL,
    evidence_item_id uuid NOT NULL,
    revision_number integer NOT NULL,
    file_object_id uuid NOT NULL,
    content_digest char(64) NOT NULL,
    mime_type varchar(120) NOT NULL,
    size_bytes bigint NOT NULL,
    capture_metadata jsonb NOT NULL,
    status varchar(32) NOT NULL,
    source_upload_session_id uuid NOT NULL,
    finalize_command_id varchar(160) NOT NULL,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_evd_revision_number UNIQUE (tenant_id, evidence_item_id, revision_number),
    CONSTRAINT uq_evd_revision_upload_session UNIQUE (tenant_id, source_upload_session_id),
    CONSTRAINT uq_evd_revision_finalize_command UNIQUE (tenant_id, finalize_command_id),
    CONSTRAINT uq_evd_revision_file UNIQUE (tenant_id, file_object_id),
    CONSTRAINT fk_evd_revision_item FOREIGN KEY (evidence_item_id)
        REFERENCES evd_evidence_item(evidence_item_id),
    CONSTRAINT fk_evd_revision_slot FOREIGN KEY (slot_id)
        REFERENCES evd_evidence_slot(slot_id),
    CONSTRAINT fk_evd_revision_task_scope FOREIGN KEY (tenant_id, task_id, project_id)
        REFERENCES tsk_task(tenant_id, task_id, project_id),
    CONSTRAINT fk_evd_revision_upload FOREIGN KEY (source_upload_session_id)
        REFERENCES evd_evidence_upload_session(upload_session_id),
    CONSTRAINT fk_evd_revision_file FOREIGN KEY (file_object_id)
        REFERENCES fil_stored_file(file_id),
    CONSTRAINT ck_evd_revision_number CHECK (revision_number > 0),
    CONSTRAINT ck_evd_revision_size CHECK (size_bytes > 0),
    CONSTRAINT ck_evd_revision_digest CHECK (content_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_evd_revision_status CHECK (
        status IN ('STORED', 'VALIDATING', 'VALIDATED', 'VALIDATION_FAILED', 'QUARANTINED', 'INVALIDATED')
    ),
    CONSTRAINT ck_evd_revision_metadata CHECK (jsonb_typeof(capture_metadata) = 'object')
);

CREATE INDEX ix_evd_revision_item_status
    ON evd_evidence_revision (tenant_id, evidence_item_id, status, revision_number DESC);
CREATE INDEX ix_evd_revision_slot_status
    ON evd_evidence_revision (tenant_id, slot_id, status);
CREATE INDEX ix_evd_revision_content_digest
    ON evd_evidence_revision (tenant_id, content_digest);

CREATE TABLE evd_evidence_command_result (
    tenant_id varchar(64) NOT NULL,
    operation_type varchar(80) NOT NULL,
    idempotency_key varchar(160) NOT NULL,
    result_id uuid NOT NULL,
    PRIMARY KEY (tenant_id, operation_type, idempotency_key)
);

-- 上传绑定在 Finalize 后标记 FINALIZED；禁止删除或篡改归属。
CREATE OR REPLACE FUNCTION evd_guard_upload_session_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'evidence upload session is immutable';
    END IF;
    IF ROW(NEW.upload_session_id, NEW.tenant_id, NEW.project_id, NEW.task_id, NEW.slot_id,
           NEW.file_id, NEW.evidence_item_id, NEW.expected_sha256, NEW.declared_mime_type,
           NEW.expected_size_bytes, NEW.original_file_name, NEW.capture_metadata,
           NEW.created_by, NEW.created_at)
       IS DISTINCT FROM
       ROW(OLD.upload_session_id, OLD.tenant_id, OLD.project_id, OLD.task_id, OLD.slot_id,
           OLD.file_id, OLD.evidence_item_id, OLD.expected_sha256, OLD.declared_mime_type,
           OLD.expected_size_bytes, OLD.original_file_name, OLD.capture_metadata,
           OLD.created_by, OLD.created_at) THEN
        RAISE EXCEPTION 'evidence upload session identity is immutable';
    END IF;
    IF OLD.status = 'FINALIZED' AND NEW.status <> 'FINALIZED' THEN
        RAISE EXCEPTION 'evidence upload session cannot leave FINALIZED';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_evd_upload_session_guard
    BEFORE UPDATE OR DELETE ON evd_evidence_upload_session
    FOR EACH ROW EXECUTE FUNCTION evd_guard_upload_session_mutation();

CREATE OR REPLACE FUNCTION evd_reject_item_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'evidence item identity is immutable';
END;
$$;

CREATE TRIGGER trg_evd_item_immutable
    BEFORE UPDATE OR DELETE ON evd_evidence_item
    FOR EACH ROW EXECUTE FUNCTION evd_reject_item_mutation();

-- Revision 核心事实不可变；仅允许 lifecycle_status 受控更新。
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
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_evd_revision_guard
    BEFORE UPDATE OR DELETE ON evd_evidence_revision
    FOR EACH ROW EXECUTE FUNCTION evd_guard_revision_mutation();

ALTER TABLE evd_evidence_upload_session
    ADD CONSTRAINT fk_evd_upload_item FOREIGN KEY (evidence_item_id)
        REFERENCES evd_evidence_item(evidence_item_id);

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES
    ('evidence.submit', '创建资料上传并 Finalize 为 EvidenceRevision', 'HIGH', now()),
    ('evidence.invalidate', '作废资料版本', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;

COMMENT ON TABLE evd_evidence_item IS '逻辑资料身份；补传新增 Revision，不覆盖 Item';
COMMENT ON TABLE evd_evidence_revision IS '不可变资料版本；Finalize 前不存在；不保存审核通过/驳回';
COMMENT ON COLUMN evd_evidence_revision.status IS 'STORED→VALIDATING→VALIDATED|VALIDATION_FAILED|QUARANTINED；VALIDATED→INVALIDATED';
COMMENT ON COLUMN evd_evidence_revision.file_object_id IS 'files 模块 StoredFile ID；禁止保存 object key 或永久 URL';
