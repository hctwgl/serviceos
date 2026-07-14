-- M40：不可变 EvidenceSetSnapshot 与成员冻结。
-- 创建后禁止增删改成员；用途 TASK_SUBMISSION 仅接受 VALIDATED Revision。

CREATE TABLE evd_evidence_set_snapshot (
    evidence_set_snapshot_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    task_id uuid NOT NULL,
    resolution_id uuid NOT NULL,
    purpose varchar(32) NOT NULL,
    member_count integer NOT NULL,
    content_digest char(64) NOT NULL,
    eligibility_summary jsonb NOT NULL,
    created_by varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT fk_evd_snapshot_resolution FOREIGN KEY (resolution_id)
        REFERENCES evd_task_evidence_resolution(resolution_id),
    CONSTRAINT fk_evd_snapshot_task_scope FOREIGN KEY (tenant_id, task_id, project_id)
        REFERENCES tsk_task(tenant_id, task_id, project_id),
    CONSTRAINT ck_evd_snapshot_purpose CHECK (purpose IN ('TASK_SUBMISSION')),
    CONSTRAINT ck_evd_snapshot_member_count CHECK (member_count >= 0),
    CONSTRAINT ck_evd_snapshot_digest CHECK (content_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_evd_snapshot_eligibility CHECK (jsonb_typeof(eligibility_summary) = 'object')
);

CREATE INDEX ix_evd_snapshot_task_created
    ON evd_evidence_set_snapshot (tenant_id, task_id, created_at DESC);

CREATE TABLE evd_evidence_set_member (
    member_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    evidence_set_snapshot_id uuid NOT NULL,
    project_id uuid NOT NULL,
    task_id uuid NOT NULL,
    slot_id uuid NOT NULL,
    evidence_item_id uuid NOT NULL,
    evidence_revision_id uuid NOT NULL,
    revision_number integer NOT NULL,
    revision_status varchar(32) NOT NULL,
    content_digest char(64) NOT NULL,
    validation_digest char(64) NOT NULL,
    member_ordinal integer NOT NULL,
    CONSTRAINT fk_evd_member_snapshot FOREIGN KEY (evidence_set_snapshot_id)
        REFERENCES evd_evidence_set_snapshot(evidence_set_snapshot_id),
    CONSTRAINT fk_evd_member_slot FOREIGN KEY (slot_id)
        REFERENCES evd_evidence_slot(slot_id),
    CONSTRAINT fk_evd_member_item FOREIGN KEY (evidence_item_id)
        REFERENCES evd_evidence_item(evidence_item_id),
    CONSTRAINT fk_evd_member_revision FOREIGN KEY (evidence_revision_id)
        REFERENCES evd_evidence_revision(evidence_revision_id),
    CONSTRAINT fk_evd_member_task_scope FOREIGN KEY (tenant_id, task_id, project_id)
        REFERENCES tsk_task(tenant_id, task_id, project_id),
    CONSTRAINT uq_evd_member_revision UNIQUE (tenant_id, evidence_set_snapshot_id, evidence_revision_id),
    CONSTRAINT uq_evd_member_item UNIQUE (tenant_id, evidence_set_snapshot_id, evidence_item_id),
    CONSTRAINT uq_evd_member_ordinal UNIQUE (tenant_id, evidence_set_snapshot_id, member_ordinal),
    CONSTRAINT ck_evd_member_revision_number CHECK (revision_number > 0),
    CONSTRAINT ck_evd_member_ordinal CHECK (member_ordinal > 0),
    CONSTRAINT ck_evd_member_status CHECK (revision_status = 'VALIDATED'),
    CONSTRAINT ck_evd_member_content_digest CHECK (content_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_evd_member_validation_digest CHECK (validation_digest ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_evd_member_snapshot
    ON evd_evidence_set_member (tenant_id, evidence_set_snapshot_id, member_ordinal);

CREATE OR REPLACE FUNCTION evd_reject_snapshot_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'evidence set snapshot is immutable';
END;
$$;

CREATE TRIGGER trg_evd_snapshot_immutable
    BEFORE UPDATE OR DELETE ON evd_evidence_set_snapshot
    FOR EACH ROW EXECUTE FUNCTION evd_reject_snapshot_mutation();

CREATE OR REPLACE FUNCTION evd_reject_snapshot_member_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'evidence set snapshot member is immutable';
END;
$$;

CREATE TRIGGER trg_evd_snapshot_member_immutable
    BEFORE UPDATE OR DELETE ON evd_evidence_set_member
    FOR EACH ROW EXECUTE FUNCTION evd_reject_snapshot_member_mutation();

COMMENT ON TABLE evd_evidence_set_snapshot IS '冻结某次提交所用资料版本集合；创建后不可变';
COMMENT ON TABLE evd_evidence_set_member IS 'Snapshot 成员；精确引用 slot/item/revision 与 validationDigest';
