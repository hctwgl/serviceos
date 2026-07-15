-- M58：BYD 提审的不可变交付意图、逐次网络尝试与外部确认。

CREATE TABLE int_outbound_delivery (
    delivery_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    connector_version_id varchar(120) NOT NULL,
    mapping_version_id varchar(120) NOT NULL,
    business_message_type varchar(80) NOT NULL,
    business_key varchar(240) NOT NULL,
    source_review_case_id uuid NOT NULL,
    source_task_id uuid NOT NULL,
    source_work_order_id uuid NOT NULL,
    source_snapshot_id uuid NOT NULL,
    source_snapshot_digest char(64) NOT NULL,
    external_order_code varchar(50) NOT NULL,
    operator_principal_id varchar(160) NOT NULL,
    operator_display_value varchar(50) NOT NULL,
    payload_object_ref varchar(512) NOT NULL,
    payload_digest char(64) NOT NULL,
    external_idempotency_key char(64) NOT NULL,
    failure_policy_version_id varchar(120) NOT NULL,
    execution_task_id uuid,
    status varchar(24) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    client_review_case_id uuid,
    review_route_id uuid,
    created_by varchar(160) NOT NULL,
    created_at timestamptz NOT NULL,
    delivered_at timestamptz,
    acknowledged_at timestamptz,
    aggregate_version bigint NOT NULL DEFAULT 1,
    CONSTRAINT fk_int_outbound_delivery_project
        FOREIGN KEY (project_id) REFERENCES prj_project(project_id),
    CONSTRAINT fk_int_outbound_delivery_task
        FOREIGN KEY (execution_task_id) REFERENCES tsk_task(task_id),
    CONSTRAINT uq_int_outbound_delivery_business
        UNIQUE (tenant_id, connector_version_id, business_message_type, business_key),
    CONSTRAINT uq_int_outbound_delivery_source_review
        UNIQUE (tenant_id, source_review_case_id, business_message_type),
    CONSTRAINT uq_int_outbound_delivery_external_key
        UNIQUE (tenant_id, connector_version_id, external_idempotency_key),
    CONSTRAINT ck_int_outbound_delivery_status CHECK (
        status IN ('PENDING', 'SENDING', 'DELIVERED', 'ACKNOWLEDGED', 'REJECTED', 'FAILED_FINAL', 'UNKNOWN')),
    CONSTRAINT ck_int_outbound_delivery_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_int_outbound_delivery_terminal_refs CHECK (
        (status = 'ACKNOWLEDGED'
            AND delivered_at IS NOT NULL AND acknowledged_at IS NOT NULL
            AND client_review_case_id IS NOT NULL AND review_route_id IS NOT NULL)
        OR
        (status = 'DELIVERED'
            AND delivered_at IS NOT NULL AND acknowledged_at IS NULL
            AND client_review_case_id IS NULL AND review_route_id IS NULL)
        OR
        (status IN ('PENDING', 'SENDING', 'REJECTED', 'FAILED_FINAL', 'UNKNOWN')
            AND acknowledged_at IS NULL
            AND client_review_case_id IS NULL AND review_route_id IS NULL)
    )
);

CREATE INDEX ix_int_outbound_delivery_project_status
    ON int_outbound_delivery (tenant_id, project_id, status, created_at DESC);
CREATE INDEX ix_int_outbound_delivery_task
    ON int_outbound_delivery (tenant_id, execution_task_id)
    WHERE execution_task_id IS NOT NULL;

CREATE TABLE int_delivery_attempt (
    delivery_attempt_id uuid PRIMARY KEY,
    delivery_id uuid NOT NULL,
    tenant_id varchar(64) NOT NULL,
    task_execution_attempt_id uuid NOT NULL,
    attempt_no integer NOT NULL,
    nonce varchar(128) NOT NULL,
    request_date date NOT NULL,
    request_digest char(64) NOT NULL,
    credential_version_id varchar(160) NOT NULL,
    status varchar(24) NOT NULL,
    http_status integer,
    response_object_ref varchar(512),
    response_digest char(64),
    result_code varchar(100),
    started_at timestamptz NOT NULL,
    finished_at timestamptz,
    CONSTRAINT fk_int_delivery_attempt_delivery
        FOREIGN KEY (delivery_id) REFERENCES int_outbound_delivery(delivery_id),
    CONSTRAINT uq_int_delivery_attempt_no UNIQUE (delivery_id, attempt_no),
    CONSTRAINT uq_int_delivery_task_attempt UNIQUE (task_execution_attempt_id),
    CONSTRAINT ck_int_delivery_attempt_no CHECK (attempt_no > 0),
    CONSTRAINT ck_int_delivery_attempt_status CHECK (
        status IN ('SENDING', 'DELIVERED', 'REJECTED', 'FAILED_FINAL', 'UNKNOWN')),
    CONSTRAINT ck_int_delivery_attempt_terminal CHECK (
        (status = 'SENDING' AND finished_at IS NULL AND result_code IS NULL)
        OR
        (status <> 'SENDING' AND finished_at IS NOT NULL AND result_code IS NOT NULL)
    )
);

CREATE INDEX ix_int_delivery_attempt_delivery
    ON int_delivery_attempt (tenant_id, delivery_id, attempt_no);

CREATE TABLE int_external_acknowledgement (
    acknowledgement_id uuid PRIMARY KEY,
    delivery_id uuid NOT NULL,
    tenant_id varchar(64) NOT NULL,
    acknowledgement_type varchar(24) NOT NULL,
    result varchar(24) NOT NULL,
    reason_code varchar(100) NOT NULL,
    response_object_ref varchar(512) NOT NULL,
    response_digest char(64) NOT NULL,
    mapping_version_id varchar(120) NOT NULL,
    received_at timestamptz NOT NULL,
    CONSTRAINT fk_int_external_ack_delivery
        FOREIGN KEY (delivery_id) REFERENCES int_outbound_delivery(delivery_id),
    CONSTRAINT uq_int_external_ack_type UNIQUE (delivery_id, acknowledgement_type),
    CONSTRAINT ck_int_external_ack_type CHECK (acknowledgement_type IN ('BUSINESS')),
    CONSTRAINT ck_int_external_ack_result CHECK (result IN ('ACCEPTED', 'REJECTED'))
);

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
    IF OLD.status IN ('ACKNOWLEDGED', 'REJECTED', 'FAILED_FINAL', 'UNKNOWN')
       AND ROW(NEW.status, NEW.execution_task_id, NEW.attempt_count,
               NEW.client_review_case_id, NEW.review_route_id,
               NEW.delivered_at, NEW.acknowledged_at, NEW.aggregate_version)
           IS DISTINCT FROM
           ROW(OLD.status, OLD.execution_task_id, OLD.attempt_count,
               OLD.client_review_case_id, OLD.review_route_id,
               OLD.delivered_at, OLD.acknowledged_at, OLD.aggregate_version) THEN
        RAISE EXCEPTION 'terminal outbound delivery is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_int_outbound_delivery_immutable
    BEFORE UPDATE OR DELETE ON int_outbound_delivery
    FOR EACH ROW EXECUTE FUNCTION int_guard_delivery_identity_mutation();

CREATE OR REPLACE FUNCTION int_guard_delivery_attempt_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'delivery attempt is immutable';
    END IF;
    IF ROW(NEW.delivery_attempt_id, NEW.delivery_id, NEW.tenant_id,
           NEW.task_execution_attempt_id, NEW.attempt_no, NEW.nonce,
           NEW.request_date, NEW.request_digest, NEW.credential_version_id, NEW.started_at)
       IS DISTINCT FROM
       ROW(OLD.delivery_attempt_id, OLD.delivery_id, OLD.tenant_id,
           OLD.task_execution_attempt_id, OLD.attempt_no, OLD.nonce,
           OLD.request_date, OLD.request_digest, OLD.credential_version_id, OLD.started_at) THEN
        RAISE EXCEPTION 'delivery attempt identity is immutable';
    END IF;
    IF OLD.status <> 'SENDING' THEN
        RAISE EXCEPTION 'terminal delivery attempt is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_int_delivery_attempt_immutable
    BEFORE UPDATE OR DELETE ON int_delivery_attempt
    FOR EACH ROW EXECUTE FUNCTION int_guard_delivery_attempt_mutation();

CREATE OR REPLACE FUNCTION int_reject_external_ack_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'external acknowledgement is immutable';
END;
$$;

CREATE TRIGGER trg_int_external_ack_immutable
    BEFORE UPDATE OR DELETE ON int_external_acknowledgement
    FOR EACH ROW EXECUTE FUNCTION int_reject_external_ack_mutation();

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES
    ('integration.submitClientReview', '创建车企审核提审交付', 'HIGH', now()),
    ('integration.readOutbound', '查看外部交付摘要', 'NORMAL', now())
ON CONFLICT (capability_code) DO NOTHING;

COMMENT ON TABLE int_outbound_delivery IS 'M58：一次不可变外部业务交付意图，重试不得改写 payload';
COMMENT ON TABLE int_delivery_attempt IS 'M58：一次真实网络尝试；不拥有业务重试时钟';
COMMENT ON TABLE int_external_acknowledgement IS 'M58：外部系统对 delivery 的不可变业务确认';
