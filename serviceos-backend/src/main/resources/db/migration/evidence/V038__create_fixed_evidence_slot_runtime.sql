CREATE TABLE evd_task_evidence_resolution (
    resolution_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    task_id uuid NOT NULL,
    configuration_bundle_id uuid NOT NULL,
    configuration_bundle_digest char(64) NOT NULL,
    stage_code varchar(64) NOT NULL,
    source_event_id uuid NOT NULL,
    source_event_digest char(64) NOT NULL,
    resolver_version varchar(32) NOT NULL,
    slot_count integer NOT NULL,
    resolved_at timestamptz NOT NULL,
    CONSTRAINT uq_evd_task_resolution UNIQUE (tenant_id, task_id),
    CONSTRAINT uq_evd_resolution_scope UNIQUE (tenant_id, resolution_id, task_id, project_id),
    CONSTRAINT fk_evd_resolution_project FOREIGN KEY (tenant_id, project_id)
        REFERENCES prj_project(tenant_id, project_id),
    CONSTRAINT fk_evd_resolution_task FOREIGN KEY (tenant_id, task_id, project_id)
        REFERENCES tsk_task(tenant_id, task_id, project_id),
    CONSTRAINT fk_evd_resolution_bundle FOREIGN KEY (tenant_id, configuration_bundle_id)
        REFERENCES cfg_configuration_bundle(tenant_id, bundle_id),
    CONSTRAINT ck_evd_resolution_bundle_digest CHECK (
        configuration_bundle_digest ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_evd_resolution_event_digest CHECK (source_event_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_evd_resolution_stage CHECK (stage_code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT ck_evd_resolution_slot_count CHECK (slot_count >= 0)
);

CREATE TABLE evd_evidence_slot (
    slot_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    task_id uuid NOT NULL,
    resolution_id uuid NOT NULL,
    template_version_id uuid NOT NULL,
    template_asset_type varchar(32) NOT NULL DEFAULT 'EVIDENCE',
    template_key varchar(128) NOT NULL,
    template_version varchar(64) NOT NULL,
    template_digest char(64) NOT NULL,
    requirement_code varchar(128) NOT NULL,
    occurrence_key varchar(64) NOT NULL,
    requirement_name varchar(200) NOT NULL,
    media_type varchar(32) NOT NULL,
    required_flag boolean NOT NULL,
    min_count integer NOT NULL,
    max_count integer,
    condition_input_digest char(64) NOT NULL,
    resolution_explanation jsonb NOT NULL,
    requirement_definition jsonb NOT NULL,
    requirement_digest char(64) NOT NULL,
    status_projection varchar(24) NOT NULL,
    resolved_at timestamptz NOT NULL,
    CONSTRAINT uq_evd_slot_occurrence UNIQUE (
        tenant_id, task_id, template_version_id, requirement_code, occurrence_key
    ),
    CONSTRAINT fk_evd_slot_resolution FOREIGN KEY (
        tenant_id, resolution_id, task_id, project_id
    ) REFERENCES evd_task_evidence_resolution(tenant_id, resolution_id, task_id, project_id),
    CONSTRAINT fk_evd_slot_template FOREIGN KEY (
        tenant_id, template_version_id, template_asset_type, template_digest
    ) REFERENCES cfg_configuration_asset_version(
        tenant_id, version_id, asset_type, content_digest
    ),
    CONSTRAINT ck_evd_slot_template_type CHECK (template_asset_type = 'EVIDENCE'),
    CONSTRAINT ck_evd_slot_count CHECK (
        min_count >= 0 AND (max_count IS NULL OR max_count >= min_count)
    ),
    CONSTRAINT ck_evd_slot_digest CHECK (
        template_digest ~ '^[0-9a-f]{64}$'
        AND condition_input_digest ~ '^[0-9a-f]{64}$'
        AND requirement_digest ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_evd_slot_media_type CHECK (
        media_type IN ('PHOTO', 'VIDEO', 'DOCUMENT', 'SIGNATURE', 'GENERATED_REPORT')
    ),
    CONSTRAINT ck_evd_slot_status CHECK (
        status_projection IN ('MISSING', 'PARTIAL', 'SATISFIED', 'INVALIDATED')
    )
);

CREATE INDEX ix_evd_slot_task_status
    ON evd_evidence_slot (tenant_id, task_id, status_projection, template_key, requirement_code);

-- 解析输入和槽位约束是创建时冻结事实；后续只允许由 EvidenceItem 投影刷新状态。
CREATE OR REPLACE FUNCTION evd_reject_resolution_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'evidence task resolution is immutable';
END;
$$;

CREATE TRIGGER trg_evd_resolution_immutable
    BEFORE UPDATE OR DELETE ON evd_task_evidence_resolution
    FOR EACH ROW EXECUTE FUNCTION evd_reject_resolution_mutation();

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
           NEW.requirement_digest, NEW.resolved_at)
       IS DISTINCT FROM
       ROW(OLD.slot_id, OLD.tenant_id, OLD.project_id, OLD.task_id, OLD.resolution_id,
           OLD.template_version_id, OLD.template_asset_type, OLD.template_key, OLD.template_version,
           OLD.template_digest, OLD.requirement_code, OLD.occurrence_key, OLD.requirement_name,
           OLD.media_type, OLD.required_flag, OLD.min_count, OLD.max_count,
           OLD.condition_input_digest, OLD.resolution_explanation, OLD.requirement_definition,
           OLD.requirement_digest, OLD.resolved_at) THEN
        RAISE EXCEPTION 'evidence slot definition is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_evd_slot_guard
    BEFORE UPDATE OR DELETE ON evd_evidence_slot
    FOR EACH ROW EXECUTE FUNCTION evd_guard_slot_mutation();

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('evidence.read', '查看任务资料槽位', 'NORMAL', now())
ON CONFLICT (capability_code) DO NOTHING;
