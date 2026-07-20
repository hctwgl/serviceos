-- M369 / ADR-090 D1-R：BUSINESS 日历截止时间；CALENDAR 资产类型；实例冻结日历版本。

ALTER TABLE cfg_configuration_asset_version
    DROP CONSTRAINT ck_cfg_asset_type;
ALTER TABLE cfg_configuration_asset_version
    ADD CONSTRAINT ck_cfg_asset_type CHECK (asset_type IN (
        'WORKFLOW', 'FORM', 'EVIDENCE', 'RULE', 'SLA', 'DISPATCH', 'PRICING',
        'INTEGRATION', 'ASSIGNEE_POLICY', 'NOTIFICATION', 'CALENDAR'
    ));

ALTER TABLE cfg_configuration_asset_draft
    DROP CONSTRAINT ck_cfg_draft_asset_type;
ALTER TABLE cfg_configuration_asset_draft
    ADD CONSTRAINT ck_cfg_draft_asset_type CHECK (asset_type IN (
        'WORKFLOW', 'FORM', 'EVIDENCE', 'RULE', 'SLA', 'DISPATCH', 'PRICING',
        'INTEGRATION', 'ASSIGNEE_POLICY', 'NOTIFICATION', 'CALENDAR'
    ));

ALTER TABLE sla_instance
    ADD COLUMN calendar_ref varchar(128),
    ADD COLUMN calendar_version_id uuid,
    ADD COLUMN calendar_semantic_version varchar(64),
    ADD COLUMN calendar_content_digest char(64);

ALTER TABLE sla_instance
    DROP CONSTRAINT ck_sla_instance_clock_mode;
ALTER TABLE sla_instance
    ADD CONSTRAINT ck_sla_instance_clock_mode CHECK (clock_mode IN ('ELAPSED', 'BUSINESS'));

ALTER TABLE sla_instance
    ADD CONSTRAINT ck_sla_instance_calendar_lock CHECK (
        (clock_mode = 'ELAPSED'
            AND calendar_ref IS NULL
            AND calendar_version_id IS NULL
            AND calendar_semantic_version IS NULL
            AND calendar_content_digest IS NULL)
        OR (clock_mode = 'BUSINESS'
            AND calendar_ref IS NOT NULL
            AND calendar_version_id IS NOT NULL
            AND calendar_semantic_version IS NOT NULL
            AND calendar_content_digest ~ '^[0-9a-f]{64}$')
    );

ALTER TABLE sla_instance
    ADD CONSTRAINT fk_sla_instance_calendar FOREIGN KEY (calendar_version_id)
        REFERENCES cfg_configuration_asset_version(version_id);

CREATE OR REPLACE FUNCTION sla_validate_instance_scope()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM tsk_task task
          JOIN cfg_configuration_bundle bundle
            ON bundle.tenant_id = task.tenant_id
           AND bundle.bundle_id = task.configuration_bundle_id
           AND bundle.manifest_digest = task.configuration_bundle_digest
          JOIN cfg_configuration_bundle_item item
            ON item.tenant_id = bundle.tenant_id
           AND item.bundle_id = bundle.bundle_id
           AND item.asset_type = 'SLA'
           AND item.asset_version_id = NEW.policy_version_id
           AND item.content_digest = NEW.policy_content_digest
         WHERE task.task_id = NEW.task_id
           AND task.tenant_id = NEW.tenant_id
           AND task.project_id = NEW.project_id
           AND task.work_order_id = NEW.work_order_id
           AND task.sla_ref = NEW.sla_ref) THEN
        RAISE EXCEPTION 'SLA Task scope or frozen reference mismatch';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM cfg_configuration_asset_version policy
         WHERE policy.version_id = NEW.policy_version_id
           AND policy.tenant_id = NEW.tenant_id
           AND policy.asset_type = 'SLA'
           AND policy.semantic_version = NEW.policy_semantic_version
           AND policy.content_digest = NEW.policy_content_digest
           AND policy.status = 'PUBLISHED') THEN
        RAISE EXCEPTION 'SLA policy identity mismatch';
    END IF;
    IF NEW.clock_mode = 'BUSINESS' THEN
        IF NOT EXISTS (
            SELECT 1
              FROM tsk_task task
              JOIN cfg_configuration_bundle bundle
                ON bundle.tenant_id = task.tenant_id
               AND bundle.bundle_id = task.configuration_bundle_id
               AND bundle.manifest_digest = task.configuration_bundle_digest
              JOIN cfg_configuration_bundle_item item
                ON item.tenant_id = bundle.tenant_id
               AND item.bundle_id = bundle.bundle_id
               AND item.asset_type = 'CALENDAR'
               AND item.asset_version_id = NEW.calendar_version_id
               AND item.content_digest = NEW.calendar_content_digest
              JOIN cfg_configuration_asset_version calendar
                ON calendar.version_id = item.asset_version_id
               AND calendar.tenant_id = item.tenant_id
               AND calendar.asset_key = NEW.calendar_ref
             WHERE task.task_id = NEW.task_id
               AND task.tenant_id = NEW.tenant_id) THEN
            RAISE EXCEPTION 'SLA calendar scope or frozen reference mismatch';
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM cfg_configuration_asset_version calendar
             WHERE calendar.version_id = NEW.calendar_version_id
               AND calendar.tenant_id = NEW.tenant_id
               AND calendar.asset_type = 'CALENDAR'
               AND calendar.asset_key = NEW.calendar_ref
               AND calendar.semantic_version = NEW.calendar_semantic_version
               AND calendar.content_digest = NEW.calendar_content_digest
               AND calendar.status = 'PUBLISHED') THEN
            RAISE EXCEPTION 'SLA calendar identity mismatch';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION sla_guard_instance_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'SLA instance is immutable';
    END IF;
    IF ROW(NEW.sla_instance_id, NEW.tenant_id, NEW.project_id, NEW.work_order_id, NEW.task_id,
           NEW.sla_ref, NEW.policy_version_id, NEW.policy_semantic_version, NEW.policy_content_digest,
           NEW.clock_mode, NEW.target_duration_seconds, NEW.start_event_id, NEW.started_at,
           NEW.deadline_at, NEW.correlation_id, NEW.created_at,
           NEW.calendar_ref, NEW.calendar_version_id, NEW.calendar_semantic_version,
           NEW.calendar_content_digest)
       IS DISTINCT FROM
       ROW(OLD.sla_instance_id, OLD.tenant_id, OLD.project_id, OLD.work_order_id, OLD.task_id,
           OLD.sla_ref, OLD.policy_version_id, OLD.policy_semantic_version, OLD.policy_content_digest,
           OLD.clock_mode, OLD.target_duration_seconds, OLD.start_event_id, OLD.started_at,
           OLD.deadline_at, OLD.correlation_id, OLD.created_at,
           OLD.calendar_ref, OLD.calendar_version_id, OLD.calendar_semantic_version,
           OLD.calendar_content_digest) THEN
        RAISE EXCEPTION 'SLA instance identity is immutable';
    END IF;
    IF OLD.status IN ('MET', 'MET_LATE') THEN
        RAISE EXCEPTION 'terminal SLA instance is immutable';
    END IF;
    IF NOT ((OLD.status = 'RUNNING' AND NEW.status IN ('BREACHED', 'MET', 'MET_LATE'))
         OR (OLD.status = 'BREACHED' AND NEW.status = 'MET_LATE')) THEN
        RAISE EXCEPTION 'invalid SLA state transition';
    END IF;
    IF NEW.aggregate_version <> OLD.aggregate_version + 1 OR NEW.updated_at < OLD.updated_at THEN
        RAISE EXCEPTION 'invalid SLA version transition';
    END IF;
    RETURN NEW;
END;
$$;

COMMENT ON COLUMN sla_instance.calendar_ref IS 'M369：BUSINESS 时钟锁定的 CALENDAR assetKey；ELAPSED 必须为空';
COMMENT ON COLUMN sla_instance.calendar_version_id IS 'M369：BUSINESS 时钟冻结的日历资产版本';
