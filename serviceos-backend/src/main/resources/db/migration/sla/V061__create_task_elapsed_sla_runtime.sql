-- M61：Task 显式锁定 SLA 配置；仅实现无默认值猜测的 ELAPSED 自然时长时钟。

ALTER TABLE tsk_task
    ADD COLUMN sla_ref varchar(120),
    ADD CONSTRAINT ck_tsk_sla_ref_workflow_context CHECK (
        sla_ref IS NULL OR workflow_definition_version_id IS NOT NULL
    );

CREATE TABLE sla_instance (
    sla_instance_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    work_order_id uuid NOT NULL,
    task_id uuid NOT NULL,
    sla_ref varchar(120) NOT NULL,
    policy_version_id uuid NOT NULL,
    policy_semantic_version varchar(64) NOT NULL,
    policy_content_digest char(64) NOT NULL,
    clock_mode varchar(24) NOT NULL,
    target_duration_seconds bigint NOT NULL,
    start_event_id uuid NOT NULL,
    started_at timestamptz NOT NULL,
    deadline_at timestamptz NOT NULL,
    status varchar(24) NOT NULL,
    breached_at timestamptz,
    breach_detected_at timestamptz,
    stop_event_id uuid,
    completed_at timestamptz,
    elapsed_seconds bigint,
    aggregate_version bigint NOT NULL,
    correlation_id varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_sla_instance_task UNIQUE (tenant_id, task_id),
    CONSTRAINT uq_sla_instance_start_event UNIQUE (tenant_id, start_event_id),
    CONSTRAINT fk_sla_instance_task FOREIGN KEY (task_id) REFERENCES tsk_task(task_id),
    CONSTRAINT fk_sla_instance_policy FOREIGN KEY (policy_version_id)
        REFERENCES cfg_configuration_asset_version(version_id),
    CONSTRAINT ck_sla_instance_policy_digest CHECK (policy_content_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_sla_instance_clock_mode CHECK (clock_mode = 'ELAPSED'),
    CONSTRAINT ck_sla_instance_duration CHECK (target_duration_seconds BETWEEN 1 AND 31536000),
    CONSTRAINT ck_sla_instance_deadline CHECK (deadline_at > started_at),
    CONSTRAINT ck_sla_instance_status CHECK (status IN ('RUNNING', 'BREACHED', 'MET', 'MET_LATE')),
    CONSTRAINT ck_sla_instance_version CHECK (aggregate_version > 0),
    CONSTRAINT ck_sla_instance_terminal_facts CHECK (
        (status = 'RUNNING' AND breached_at IS NULL AND breach_detected_at IS NULL
            AND stop_event_id IS NULL AND completed_at IS NULL AND elapsed_seconds IS NULL)
        OR (status = 'BREACHED' AND breached_at = deadline_at AND breach_detected_at IS NOT NULL
            AND stop_event_id IS NULL AND completed_at IS NULL AND elapsed_seconds IS NULL)
        OR (status = 'MET' AND breached_at IS NULL AND breach_detected_at IS NULL
            AND stop_event_id IS NOT NULL AND completed_at IS NOT NULL AND elapsed_seconds IS NOT NULL
            AND completed_at <= deadline_at)
        OR (status = 'MET_LATE' AND breached_at = deadline_at AND breach_detected_at IS NOT NULL
            AND stop_event_id IS NOT NULL AND completed_at IS NOT NULL AND elapsed_seconds IS NOT NULL
            AND completed_at > deadline_at)
    )
);

CREATE INDEX ix_sla_instance_status_deadline
    ON sla_instance (status, deadline_at, sla_instance_id)
    WHERE status IN ('RUNNING', 'BREACHED');

CREATE TABLE sla_clock_segment (
    segment_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    sla_instance_id uuid NOT NULL,
    segment_no integer NOT NULL,
    segment_type varchar(24) NOT NULL,
    started_at timestamptz NOT NULL,
    ended_at timestamptz,
    elapsed_seconds bigint,
    start_event_id uuid NOT NULL,
    end_event_id uuid,
    CONSTRAINT uq_sla_segment_no UNIQUE (tenant_id, sla_instance_id, segment_no),
    CONSTRAINT fk_sla_segment_instance FOREIGN KEY (sla_instance_id)
        REFERENCES sla_instance(sla_instance_id),
    CONSTRAINT ck_sla_segment_no CHECK (segment_no > 0),
    CONSTRAINT ck_sla_segment_type CHECK (segment_type = 'RUNNING'),
    CONSTRAINT ck_sla_segment_end CHECK (
        (ended_at IS NULL AND elapsed_seconds IS NULL AND end_event_id IS NULL)
        OR (ended_at >= started_at AND elapsed_seconds >= 0 AND end_event_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_sla_open_segment
    ON sla_clock_segment (tenant_id, sla_instance_id) WHERE ended_at IS NULL;

CREATE TABLE sla_milestone (
    milestone_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    sla_instance_id uuid NOT NULL,
    milestone_type varchar(40) NOT NULL,
    scheduled_at timestamptz NOT NULL,
    status varchar(24) NOT NULL,
    triggered_at timestamptz,
    detected_at timestamptz,
    trigger_event_id uuid,
    CONSTRAINT uq_sla_milestone_type UNIQUE (tenant_id, sla_instance_id, milestone_type),
    CONSTRAINT fk_sla_milestone_instance FOREIGN KEY (sla_instance_id)
        REFERENCES sla_instance(sla_instance_id),
    CONSTRAINT ck_sla_milestone_type CHECK (milestone_type = 'TARGET_DUE'),
    CONSTRAINT ck_sla_milestone_status CHECK (status IN ('PENDING', 'TRIGGERED', 'CANCELLED')),
    CONSTRAINT ck_sla_milestone_trigger CHECK (
        (status IN ('PENDING', 'CANCELLED') AND triggered_at IS NULL
            AND detected_at IS NULL AND trigger_event_id IS NULL)
        OR (status = 'TRIGGERED' AND triggered_at = scheduled_at
            AND detected_at IS NOT NULL AND trigger_event_id IS NOT NULL)
    )
);

CREATE INDEX ix_sla_milestone_due
    ON sla_milestone (scheduled_at, milestone_id) WHERE status = 'PENDING';

-- 跨模块 FK 无法同时核验 tenant、SLA 类型与冻结摘要，因此在数据库入口失败关闭。
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
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_sla_instance_scope
    BEFORE INSERT ON sla_instance
    FOR EACH ROW EXECUTE FUNCTION sla_validate_instance_scope();

CREATE OR REPLACE FUNCTION sla_guard_instance_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'SLA instance is immutable';
    END IF;
    IF ROW(NEW.sla_instance_id, NEW.tenant_id, NEW.project_id, NEW.work_order_id, NEW.task_id,
           NEW.sla_ref, NEW.policy_version_id, NEW.policy_semantic_version, NEW.policy_content_digest,
           NEW.clock_mode, NEW.target_duration_seconds, NEW.start_event_id, NEW.started_at,
           NEW.deadline_at, NEW.correlation_id, NEW.created_at)
       IS DISTINCT FROM
       ROW(OLD.sla_instance_id, OLD.tenant_id, OLD.project_id, OLD.work_order_id, OLD.task_id,
           OLD.sla_ref, OLD.policy_version_id, OLD.policy_semantic_version, OLD.policy_content_digest,
           OLD.clock_mode, OLD.target_duration_seconds, OLD.start_event_id, OLD.started_at,
           OLD.deadline_at, OLD.correlation_id, OLD.created_at) THEN
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

CREATE TRIGGER trg_sla_instance_guard
    BEFORE UPDATE OR DELETE ON sla_instance
    FOR EACH ROW EXECUTE FUNCTION sla_guard_instance_mutation();

CREATE OR REPLACE FUNCTION sla_guard_segment_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'SLA clock segment is immutable';
    END IF;
    IF ROW(NEW.segment_id, NEW.tenant_id, NEW.sla_instance_id, NEW.segment_no,
           NEW.segment_type, NEW.started_at, NEW.start_event_id)
       IS DISTINCT FROM
       ROW(OLD.segment_id, OLD.tenant_id, OLD.sla_instance_id, OLD.segment_no,
           OLD.segment_type, OLD.started_at, OLD.start_event_id)
       OR OLD.ended_at IS NOT NULL OR NEW.ended_at IS NULL THEN
        RAISE EXCEPTION 'invalid SLA clock segment mutation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_sla_segment_guard
    BEFORE UPDATE OR DELETE ON sla_clock_segment
    FOR EACH ROW EXECUTE FUNCTION sla_guard_segment_mutation();

CREATE OR REPLACE FUNCTION sla_guard_milestone_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'SLA milestone is immutable';
    END IF;
    IF ROW(NEW.milestone_id, NEW.tenant_id, NEW.sla_instance_id,
           NEW.milestone_type, NEW.scheduled_at)
       IS DISTINCT FROM
       ROW(OLD.milestone_id, OLD.tenant_id, OLD.sla_instance_id,
           OLD.milestone_type, OLD.scheduled_at)
       OR OLD.status <> 'PENDING' OR NEW.status NOT IN ('TRIGGERED', 'CANCELLED') THEN
        RAISE EXCEPTION 'invalid SLA milestone mutation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_sla_milestone_guard
    BEFORE UPDATE OR DELETE ON sla_milestone
    FOR EACH ROW EXECUTE FUNCTION sla_guard_milestone_mutation();

COMMENT ON COLUMN tsk_task.sla_ref IS 'M61：工作流节点冻结的 SLA policyKey；NULL 表示该 Task 明确不启用 SLA';
COMMENT ON TABLE sla_instance IS 'M61：锁定 Task、SLA 配置版本和自然时长截止时间的权威时钟';
COMMENT ON TABLE sla_clock_segment IS 'M61：ELAPSED Task SLA 的运行时间片；结束后不可修改';
COMMENT ON TABLE sla_milestone IS 'M61：可对账的 TARGET_DUE 里程碑；重复扫描不得重复触发';
