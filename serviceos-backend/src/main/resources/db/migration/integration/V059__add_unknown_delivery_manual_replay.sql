-- M59：UNKNOWN 外发结果的高风险人工重发授权。原 Delivery/payload 与历史 attempt 均不改写。

CREATE TABLE int_delivery_replay_request (
    replay_request_id uuid PRIMARY KEY,
    delivery_id uuid NOT NULL,
    tenant_id varchar(64) NOT NULL,
    expected_delivery_version bigint NOT NULL,
    reason varchar(1000) NOT NULL,
    approval_ref varchar(160) NOT NULL,
    requested_by varchar(160) NOT NULL,
    execution_task_id uuid,
    status varchar(24) NOT NULL,
    result_code varchar(100),
    requested_at timestamptz NOT NULL,
    started_at timestamptz,
    finished_at timestamptz,
    CONSTRAINT fk_int_delivery_replay_delivery
        FOREIGN KEY (delivery_id) REFERENCES int_outbound_delivery(delivery_id),
    CONSTRAINT fk_int_delivery_replay_task
        FOREIGN KEY (execution_task_id) REFERENCES tsk_task(task_id),
    CONSTRAINT uq_int_delivery_replay_task UNIQUE (execution_task_id),
    CONSTRAINT ck_int_delivery_replay_version CHECK (expected_delivery_version > 0),
    CONSTRAINT ck_int_delivery_replay_status CHECK (
        status IN ('REQUESTED', 'EXECUTING', 'DELIVERED', 'REJECTED', 'FAILED_FINAL', 'UNKNOWN')),
    CONSTRAINT ck_int_delivery_replay_lifecycle CHECK (
        (status = 'REQUESTED' AND execution_task_id IS NOT NULL
            AND started_at IS NULL AND finished_at IS NULL AND result_code IS NULL)
        OR
        (status = 'EXECUTING' AND execution_task_id IS NOT NULL
            AND started_at IS NOT NULL AND finished_at IS NULL AND result_code IS NULL)
        OR
        (status IN ('DELIVERED', 'REJECTED', 'FAILED_FINAL', 'UNKNOWN')
            AND execution_task_id IS NOT NULL AND finished_at IS NOT NULL AND result_code IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_int_delivery_replay_active
    ON int_delivery_replay_request (delivery_id)
    WHERE status IN ('REQUESTED', 'EXECUTING');
CREATE INDEX ix_int_delivery_replay_delivery
    ON int_delivery_replay_request (tenant_id, delivery_id, requested_at DESC);

CREATE OR REPLACE FUNCTION int_guard_delivery_replay_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'delivery replay request is immutable';
    END IF;
    IF ROW(NEW.replay_request_id, NEW.delivery_id, NEW.tenant_id,
           NEW.expected_delivery_version, NEW.reason, NEW.approval_ref,
           NEW.requested_by, NEW.execution_task_id, NEW.requested_at)
       IS DISTINCT FROM
       ROW(OLD.replay_request_id, OLD.delivery_id, OLD.tenant_id,
           OLD.expected_delivery_version, OLD.reason, OLD.approval_ref,
           OLD.requested_by, OLD.execution_task_id, OLD.requested_at) THEN
        RAISE EXCEPTION 'delivery replay request identity and authorization are immutable';
    END IF;
    IF OLD.status IN ('DELIVERED', 'REJECTED', 'FAILED_FINAL', 'UNKNOWN') THEN
        RAISE EXCEPTION 'terminal delivery replay request is immutable';
    END IF;
    IF OLD.status = 'REQUESTED' AND NEW.status NOT IN ('EXECUTING', 'FAILED_FINAL') THEN
        RAISE EXCEPTION 'requested delivery replay has an invalid transition';
    END IF;
    IF OLD.status = 'EXECUTING' AND NEW.status NOT IN ('DELIVERED', 'REJECTED', 'FAILED_FINAL', 'UNKNOWN') THEN
        RAISE EXCEPTION 'executing delivery replay has an invalid transition';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_int_delivery_replay_immutable
    BEFORE UPDATE OR DELETE ON int_delivery_replay_request
    FOR EACH ROW EXECUTE FUNCTION int_guard_delivery_replay_mutation();

-- M58 将 UNKNOWN 设为自动执行的终态；M59 只在已登记且正在执行的人工 ReplayRequest 下开放一次迁移。
CREATE OR REPLACE FUNCTION int_guard_delivery_identity_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'outbound delivery is immutable';
    END IF;
    IF ROW(NEW.delivery_id, NEW.tenant_id, NEW.project_id, NEW.connector_version_id,
           NEW.mapping_version_id, NEW.business_message_type, NEW.business_key,
           NEW.source_review_case_id, NEW.source_task_id, NEW.source_work_order_id,
           NEW.source_snapshot_id, NEW.source_snapshot_digest, NEW.external_order_code,
           NEW.operator_principal_id, NEW.operator_display_value, NEW.payload_object_ref,
           NEW.payload_digest, NEW.external_idempotency_key, NEW.failure_policy_version_id,
           NEW.created_by, NEW.created_at)
       IS DISTINCT FROM
       ROW(OLD.delivery_id, OLD.tenant_id, OLD.project_id, OLD.connector_version_id,
           OLD.mapping_version_id, OLD.business_message_type, OLD.business_key,
           OLD.source_review_case_id, OLD.source_task_id, OLD.source_work_order_id,
           OLD.source_snapshot_id, OLD.source_snapshot_digest, OLD.external_order_code,
           OLD.operator_principal_id, OLD.operator_display_value, OLD.payload_object_ref,
           OLD.payload_digest, OLD.external_idempotency_key, OLD.failure_policy_version_id,
           OLD.created_by, OLD.created_at) THEN
        RAISE EXCEPTION 'outbound delivery identity and payload are immutable';
    END IF;
    IF OLD.status IN ('ACKNOWLEDGED', 'REJECTED', 'FAILED_FINAL')
       AND ROW(NEW.status, NEW.execution_task_id, NEW.attempt_count,
               NEW.client_review_case_id, NEW.review_route_id,
               NEW.delivered_at, NEW.acknowledged_at, NEW.aggregate_version)
           IS DISTINCT FROM
           ROW(OLD.status, OLD.execution_task_id, OLD.attempt_count,
               OLD.client_review_case_id, OLD.review_route_id,
               OLD.delivered_at, OLD.acknowledged_at, OLD.aggregate_version) THEN
        RAISE EXCEPTION 'terminal outbound delivery is immutable';
    END IF;
    IF OLD.status = 'UNKNOWN'
       AND ROW(NEW.status, NEW.execution_task_id, NEW.attempt_count,
               NEW.client_review_case_id, NEW.review_route_id,
               NEW.delivered_at, NEW.acknowledged_at, NEW.aggregate_version)
           IS DISTINCT FROM
           ROW(OLD.status, OLD.execution_task_id, OLD.attempt_count,
               OLD.client_review_case_id, OLD.review_route_id,
               OLD.delivered_at, OLD.acknowledged_at, OLD.aggregate_version)
       AND NOT (NEW.status = 'SENDING'
           AND NEW.execution_task_id IS NOT DISTINCT FROM OLD.execution_task_id
           AND NEW.attempt_count = OLD.attempt_count + 1
           AND NEW.client_review_case_id IS NOT DISTINCT FROM OLD.client_review_case_id
           AND NEW.review_route_id IS NOT DISTINCT FROM OLD.review_route_id
           AND NEW.delivered_at IS NOT DISTINCT FROM OLD.delivered_at
           AND NEW.acknowledged_at IS NOT DISTINCT FROM OLD.acknowledged_at
           AND NEW.aggregate_version = OLD.aggregate_version + 1
           AND EXISTS (
               SELECT 1 FROM int_delivery_replay_request replay
                WHERE replay.delivery_id = OLD.delivery_id AND replay.status = 'EXECUTING')) THEN
        RAISE EXCEPTION 'unknown delivery requires an executing replay request';
    END IF;
    RETURN NEW;
END;
$$;

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('integration.retryUnknownDelivery', '人工重发未知结果的外部交付', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;

COMMENT ON TABLE int_delivery_replay_request IS
    'M59：UNKNOWN Delivery 的不可变人工重发授权、审批引用和执行结果';
