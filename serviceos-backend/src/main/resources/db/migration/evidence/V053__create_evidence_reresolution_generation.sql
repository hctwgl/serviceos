-- M53 将 M52 的单次解析扩展为只追加 generation；历史 resolution/slot/snapshot 不改写。
ALTER TABLE evd_task_evidence_resolution
    ADD COLUMN generation_no integer,
    ADD COLUMN condition_fact_type varchar(32),
    ADD COLUMN condition_fact_ref varchar(160),
    ADD COLUMN condition_fact_revision integer,
    ADD COLUMN previous_resolution_id uuid;

-- 不可变触发器只在一次性迁移回填窗口关闭；运行时没有双写或默认值。
ALTER TABLE evd_task_evidence_resolution DISABLE TRIGGER trg_evd_resolution_immutable;
UPDATE evd_task_evidence_resolution
   SET generation_no = 1,
       condition_fact_type = 'TASK_CREATED',
       condition_fact_ref = source_event_id::text,
       condition_fact_revision = 0;
ALTER TABLE evd_task_evidence_resolution ENABLE TRIGGER trg_evd_resolution_immutable;

ALTER TABLE evd_task_evidence_resolution
    ALTER COLUMN generation_no SET NOT NULL,
    ALTER COLUMN condition_fact_type SET NOT NULL,
    ALTER COLUMN condition_fact_ref SET NOT NULL,
    ALTER COLUMN condition_fact_revision SET NOT NULL,
    DROP CONSTRAINT uq_evd_task_resolution,
    ADD CONSTRAINT uq_evd_resolution_generation UNIQUE (tenant_id, task_id, generation_no),
    ADD CONSTRAINT uq_evd_resolution_fact UNIQUE (
        tenant_id, task_id, condition_fact_type, condition_fact_revision
    ),
    ADD CONSTRAINT fk_evd_resolution_previous FOREIGN KEY (
        tenant_id, previous_resolution_id, task_id, project_id
    ) REFERENCES evd_task_evidence_resolution(tenant_id, resolution_id, task_id, project_id),
    ADD CONSTRAINT ck_evd_resolution_generation CHECK (generation_no >= 1),
    ADD CONSTRAINT ck_evd_resolution_fact_revision CHECK (condition_fact_revision >= 0),
    ADD CONSTRAINT ck_evd_resolution_fact_type CHECK (
        condition_fact_type IN ('TASK_CREATED', 'FORM_SUBMISSION')
    );

ALTER TABLE evd_evidence_slot
    ADD COLUMN slot_generation integer,
    ADD COLUMN supersedes_slot_id uuid;

ALTER TABLE evd_evidence_slot DISABLE TRIGGER trg_evd_slot_guard;
UPDATE evd_evidence_slot SET slot_generation = 1;
ALTER TABLE evd_evidence_slot ENABLE TRIGGER trg_evd_slot_guard;

ALTER TABLE evd_evidence_slot
    ALTER COLUMN slot_generation SET NOT NULL,
    DROP CONSTRAINT uq_evd_slot_occurrence,
    ADD CONSTRAINT uq_evd_slot_occurrence_generation UNIQUE (
        tenant_id, task_id, template_version_id, requirement_code, occurrence_key, slot_generation
    ),
    ADD CONSTRAINT uq_evd_slot_scope UNIQUE (tenant_id, slot_id, task_id, project_id),
    ADD CONSTRAINT fk_evd_slot_supersedes FOREIGN KEY (
        tenant_id, supersedes_slot_id, task_id, project_id
    ) REFERENCES evd_evidence_slot(tenant_id, slot_id, task_id, project_id),
    ADD CONSTRAINT ck_evd_slot_generation CHECK (slot_generation >= 1);

CREATE TABLE evd_evidence_resolution_member (
    member_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    task_id uuid NOT NULL,
    resolution_id uuid NOT NULL,
    template_version_id uuid NOT NULL,
    requirement_code varchar(128) NOT NULL,
    occurrence_key varchar(64) NOT NULL,
    condition_result boolean NOT NULL,
    active_slot_id uuid,
    previous_slot_id uuid,
    transition varchar(32) NOT NULL,
    required_disposition varchar(32) NOT NULL,
    counting_item_count integer NOT NULL,
    condition_input_digest char(64) NOT NULL,
    resolution_explanation jsonb NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_evd_resolution_member_identity UNIQUE (
        tenant_id, resolution_id, template_version_id, requirement_code, occurrence_key
    ),
    CONSTRAINT uq_evd_resolution_member_scope UNIQUE (
        tenant_id, member_id, resolution_id, task_id, project_id
    ),
    CONSTRAINT fk_evd_member_resolution FOREIGN KEY (
        tenant_id, resolution_id, task_id, project_id
    ) REFERENCES evd_task_evidence_resolution(tenant_id, resolution_id, task_id, project_id),
    CONSTRAINT fk_evd_member_active_slot FOREIGN KEY (
        tenant_id, active_slot_id, task_id, project_id
    ) REFERENCES evd_evidence_slot(tenant_id, slot_id, task_id, project_id),
    CONSTRAINT fk_evd_member_previous_slot FOREIGN KEY (
        tenant_id, previous_slot_id, task_id, project_id
    ) REFERENCES evd_evidence_slot(tenant_id, slot_id, task_id, project_id),
    CONSTRAINT ck_evd_member_transition CHECK (
        transition IN ('ACTIVATED', 'UNCHANGED_ACTIVE', 'DEACTIVATED', 'UNCHANGED_INACTIVE')
    ),
    CONSTRAINT ck_evd_member_disposition CHECK (
        required_disposition IN ('NONE', 'REVIEW_REQUIRED')
    ),
    CONSTRAINT ck_evd_member_item_count CHECK (counting_item_count >= 0),
    CONSTRAINT ck_evd_member_digest CHECK (condition_input_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_evd_member_explanation CHECK (jsonb_typeof(resolution_explanation) = 'object'),
    CONSTRAINT ck_evd_member_slot_shape CHECK (
        (condition_result AND active_slot_id IS NOT NULL)
        OR (NOT condition_result AND active_slot_id IS NULL)
    ),
    CONSTRAINT ck_evd_member_review_shape CHECK (
        required_disposition = 'NONE'
        OR (transition IN ('DEACTIVATED', 'UNCHANGED_INACTIVE')
            AND previous_slot_id IS NOT NULL AND counting_item_count > 0)
    )
);

-- M37～M52 已存在的槽位全部属于 generation 1 的活动成员；false 决策仍保存在解析级解释中。
INSERT INTO evd_evidence_resolution_member (
    member_id, tenant_id, project_id, task_id, resolution_id,
    template_version_id, requirement_code, occurrence_key, condition_result,
    active_slot_id, previous_slot_id, transition, required_disposition,
    counting_item_count, condition_input_digest, resolution_explanation, created_at
)
SELECT slot.slot_id, slot.tenant_id, slot.project_id, slot.task_id, slot.resolution_id,
       slot.template_version_id, slot.requirement_code, slot.occurrence_key, true,
       slot.slot_id, NULL, 'ACTIVATED', 'NONE',
       (SELECT count(*)
          FROM evd_evidence_item item
         WHERE item.tenant_id = slot.tenant_id
           AND item.slot_id = slot.slot_id
           AND EXISTS (
               SELECT 1 FROM evd_evidence_revision revision
                WHERE revision.tenant_id = item.tenant_id
                  AND revision.evidence_item_id = item.evidence_item_id
                  AND revision.status IN ('STORED', 'VALIDATING', 'VALIDATED')
           )),
       slot.condition_input_digest, slot.resolution_explanation, slot.resolved_at
  FROM evd_evidence_slot slot;

CREATE TABLE evd_evidence_condition_disposition (
    disposition_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    task_id uuid NOT NULL,
    resolution_id uuid NOT NULL,
    member_id uuid NOT NULL,
    slot_id uuid NOT NULL,
    decision varchar(24) NOT NULL,
    reason_code varchar(100) NOT NULL,
    review_ref varchar(200) NOT NULL,
    decided_by varchar(128) NOT NULL,
    decided_at timestamptz NOT NULL,
    request_digest char(64) NOT NULL,
    CONSTRAINT uq_evd_condition_disposition_member UNIQUE (tenant_id, member_id),
    CONSTRAINT fk_evd_disposition_member FOREIGN KEY (
        tenant_id, member_id, resolution_id, task_id, project_id
    ) REFERENCES evd_evidence_resolution_member(
        tenant_id, member_id, resolution_id, task_id, project_id
    ),
    CONSTRAINT fk_evd_disposition_slot FOREIGN KEY (
        tenant_id, slot_id, task_id, project_id
    ) REFERENCES evd_evidence_slot(tenant_id, slot_id, task_id, project_id),
    CONSTRAINT ck_evd_disposition_decision CHECK (decision IN ('KEEP', 'INVALIDATE')),
    CONSTRAINT ck_evd_disposition_digest CHECK (request_digest ~ '^[0-9a-f]{64}$')
);

CREATE OR REPLACE FUNCTION evd_reject_generation_fact_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'evidence generation fact is immutable';
END;
$$;

CREATE TRIGGER trg_evd_resolution_member_immutable
    BEFORE UPDATE OR DELETE ON evd_evidence_resolution_member
    FOR EACH ROW EXECUTE FUNCTION evd_reject_generation_fact_mutation();

CREATE TRIGGER trg_evd_condition_disposition_immutable
    BEFORE UPDATE OR DELETE ON evd_evidence_condition_disposition
    FOR EACH ROW EXECUTE FUNCTION evd_reject_generation_fact_mutation();

-- 槽位定义新增的世代与 lineage 同样属于不可变事实，只有 status_projection 可刷新。
CREATE OR REPLACE FUNCTION evd_guard_slot_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'evidence slot is immutable';
    END IF;
    IF ROW(NEW.slot_id, NEW.tenant_id, NEW.project_id, NEW.task_id, NEW.resolution_id,
           NEW.template_version_id, NEW.template_asset_type, NEW.template_key, NEW.template_version,
           NEW.template_digest, NEW.requirement_code, NEW.occurrence_key, NEW.requirement_name,
           NEW.media_type, NEW.required_flag, NEW.min_count, NEW.max_count,
           NEW.condition_input_digest, NEW.resolution_explanation, NEW.requirement_definition,
           NEW.requirement_digest, NEW.resolved_at, NEW.slot_generation, NEW.supersedes_slot_id)
       IS DISTINCT FROM
       ROW(OLD.slot_id, OLD.tenant_id, OLD.project_id, OLD.task_id, OLD.resolution_id,
           OLD.template_version_id, OLD.template_asset_type, OLD.template_key, OLD.template_version,
           OLD.template_digest, OLD.requirement_code, OLD.occurrence_key, OLD.requirement_name,
           OLD.media_type, OLD.required_flag, OLD.min_count, OLD.max_count,
           OLD.condition_input_digest, OLD.resolution_explanation, OLD.requirement_definition,
           OLD.requirement_digest, OLD.resolved_at, OLD.slot_generation, OLD.supersedes_slot_id) THEN
        RAISE EXCEPTION 'evidence slot definition is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE INDEX ix_evd_resolution_latest
    ON evd_task_evidence_resolution (tenant_id, task_id, generation_no DESC);
CREATE INDEX ix_evd_member_latest_active
    ON evd_evidence_resolution_member (tenant_id, task_id, resolution_id, condition_result);

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('evidence.condition-disposition', '处置条件变化后的已提交资料', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;
