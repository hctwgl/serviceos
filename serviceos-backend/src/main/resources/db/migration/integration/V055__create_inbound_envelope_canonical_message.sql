-- M56：通用入站 Envelope / CanonicalMessage 权威事实。

CREATE TABLE int_inbound_envelope (
    inbound_envelope_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid,
    connector_version_id varchar(120) NOT NULL,
    message_type varchar(80) NOT NULL,
    transport_dedup_key char(64) NOT NULL,
    external_message_id varchar(160) NOT NULL,
    received_at timestamptz NOT NULL,
    raw_payload_object_ref varchar(500) NOT NULL,
    raw_payload_digest char(64) NOT NULL,
    canonical_payload_digest char(64),
    signature_status varchar(24) NOT NULL,
    processing_status varchar(24) NOT NULL,
    mapping_version_id varchar(120),
    canonical_message_id uuid,
    result_code varchar(80),
    result_type varchar(80),
    result_id varchar(160),
    correlation_id varchar(128) NOT NULL,
    completed_at timestamptz,
    CONSTRAINT fk_int_inbound_envelope_project
        FOREIGN KEY (project_id) REFERENCES prj_project(project_id),
    CONSTRAINT uq_int_inbound_envelope_transport
        UNIQUE (tenant_id, connector_version_id, transport_dedup_key),
    CONSTRAINT ck_int_inbound_envelope_signature
        CHECK (signature_status = 'VALID'),
    CONSTRAINT ck_int_inbound_envelope_status
        CHECK (processing_status IN ('RECEIVED', 'COMPLETED', 'REJECTED')),
    CONSTRAINT ck_int_inbound_envelope_completion CHECK (
        (processing_status = 'RECEIVED' AND completed_at IS NULL AND result_code IS NULL)
        OR
        (processing_status IN ('COMPLETED', 'REJECTED')
            AND completed_at IS NOT NULL AND result_code IS NOT NULL)
    )
);

CREATE TABLE int_canonical_message (
    canonical_message_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    connector_version_id varchar(120) NOT NULL,
    message_type varchar(80) NOT NULL,
    business_key varchar(200) NOT NULL,
    payload_object_ref varchar(500) NOT NULL,
    payload_digest char(64) NOT NULL,
    mapping_version_id varchar(120) NOT NULL,
    processing_status varchar(24) NOT NULL,
    result_code varchar(80),
    result_type varchar(80),
    result_id varchar(160),
    source_envelope_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    processed_at timestamptz,
    CONSTRAINT fk_int_canonical_message_project
        FOREIGN KEY (project_id) REFERENCES prj_project(project_id),
    CONSTRAINT fk_int_canonical_message_source_envelope
        FOREIGN KEY (source_envelope_id) REFERENCES int_inbound_envelope(inbound_envelope_id),
    CONSTRAINT uq_int_canonical_message_business_key
        UNIQUE (tenant_id, connector_version_id, message_type, business_key),
    CONSTRAINT ck_int_canonical_message_status
        CHECK (processing_status IN ('PROCESSING', 'COMPLETED')),
    CONSTRAINT ck_int_canonical_message_completion CHECK (
        (processing_status = 'PROCESSING' AND processed_at IS NULL AND result_code IS NULL)
        OR
        (processing_status = 'COMPLETED' AND processed_at IS NOT NULL
            AND result_code IS NOT NULL AND result_type IS NOT NULL AND result_id IS NOT NULL)
    )
);

ALTER TABLE int_inbound_envelope
    ADD CONSTRAINT fk_int_inbound_envelope_canonical
        FOREIGN KEY (canonical_message_id) REFERENCES int_canonical_message(canonical_message_id);

CREATE INDEX ix_int_inbound_envelope_project_received
    ON int_inbound_envelope (tenant_id, project_id, received_at DESC);
CREATE INDEX ix_int_inbound_envelope_status_received
    ON int_inbound_envelope (tenant_id, processing_status, received_at);
CREATE INDEX ix_int_canonical_message_result
    ON int_canonical_message (tenant_id, result_type, result_id);

-- 该表过去由 repeatable migration 创建。V055 先保证空库 versioned 阶段可引用，随后 repeatable 保持幂等。
CREATE TABLE IF NOT EXISTS int_inbound_replay_guard (
    app_key varchar(128) NOT NULL,
    nonce varchar(128) NOT NULL,
    request_time_epoch bigint NOT NULL,
    payload_digest char(64) NOT NULL,
    first_seen_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    result_digest char(64),
    PRIMARY KEY (app_key, nonce, request_time_epoch)
);

ALTER TABLE int_inbound_replay_guard
    ADD COLUMN inbound_envelope_id uuid,
    ADD CONSTRAINT fk_int_inbound_replay_envelope
        FOREIGN KEY (inbound_envelope_id) REFERENCES int_inbound_envelope(inbound_envelope_id);

CREATE INDEX IF NOT EXISTS idx_int_inbound_replay_guard_expires_at
    ON int_inbound_replay_guard (expires_at);

CREATE OR REPLACE FUNCTION int_guard_inbound_envelope_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'inbound envelope is immutable';
    END IF;
    IF ROW(NEW.inbound_envelope_id, NEW.tenant_id, NEW.connector_version_id,
           NEW.message_type, NEW.transport_dedup_key, NEW.external_message_id,
           NEW.received_at, NEW.raw_payload_object_ref, NEW.raw_payload_digest,
           NEW.signature_status, NEW.correlation_id)
       IS DISTINCT FROM
       ROW(OLD.inbound_envelope_id, OLD.tenant_id, OLD.connector_version_id,
           OLD.message_type, OLD.transport_dedup_key, OLD.external_message_id,
           OLD.received_at, OLD.raw_payload_object_ref, OLD.raw_payload_digest,
           OLD.signature_status, OLD.correlation_id) THEN
        RAISE EXCEPTION 'inbound envelope identity is immutable';
    END IF;
    IF OLD.processing_status <> 'RECEIVED'
       AND ROW(NEW.processing_status, NEW.project_id, NEW.canonical_payload_digest,
               NEW.mapping_version_id, NEW.canonical_message_id, NEW.result_code,
               NEW.result_type, NEW.result_id, NEW.completed_at)
           IS DISTINCT FROM
           ROW(OLD.processing_status, OLD.project_id, OLD.canonical_payload_digest,
               OLD.mapping_version_id, OLD.canonical_message_id, OLD.result_code,
               OLD.result_type, OLD.result_id, OLD.completed_at) THEN
        RAISE EXCEPTION 'completed inbound envelope is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_int_inbound_envelope_immutable
    BEFORE UPDATE OR DELETE ON int_inbound_envelope
    FOR EACH ROW EXECUTE FUNCTION int_guard_inbound_envelope_mutation();

CREATE OR REPLACE FUNCTION int_guard_canonical_message_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'canonical message is immutable';
    END IF;
    IF ROW(NEW.canonical_message_id, NEW.tenant_id, NEW.project_id,
           NEW.connector_version_id, NEW.message_type, NEW.business_key,
           NEW.payload_object_ref, NEW.payload_digest, NEW.mapping_version_id,
           NEW.source_envelope_id, NEW.created_at)
       IS DISTINCT FROM
       ROW(OLD.canonical_message_id, OLD.tenant_id, OLD.project_id,
           OLD.connector_version_id, OLD.message_type, OLD.business_key,
           OLD.payload_object_ref, OLD.payload_digest, OLD.mapping_version_id,
           OLD.source_envelope_id, OLD.created_at) THEN
        RAISE EXCEPTION 'canonical message identity is immutable';
    END IF;
    IF OLD.processing_status <> 'PROCESSING'
       AND ROW(NEW.processing_status, NEW.result_code, NEW.result_type,
               NEW.result_id, NEW.processed_at)
           IS DISTINCT FROM
           ROW(OLD.processing_status, OLD.result_code, OLD.result_type,
               OLD.result_id, OLD.processed_at) THEN
        RAISE EXCEPTION 'completed canonical message is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_int_canonical_message_immutable
    BEFORE UPDATE OR DELETE ON int_canonical_message
    FOR EACH ROW EXECUTE FUNCTION int_guard_canonical_message_mutation();

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('integration.readInbound', '查看入站消息和标准消息摘要', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;

COMMENT ON TABLE int_inbound_envelope IS 'M56：验签后收到的不可变外部消息与处理投影';
COMMENT ON TABLE int_canonical_message IS 'M56：稳定业务语义、映射版本和领域命令结果';
