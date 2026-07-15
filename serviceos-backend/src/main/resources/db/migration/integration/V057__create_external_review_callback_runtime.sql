-- M57：纠正 BYD V7.3.1 日期级 replay key，并建立外部审核路由与逐订单批次结果。

ALTER TABLE int_inbound_replay_guard
    RENAME COLUMN request_time_epoch TO request_date_epoch_day;

-- 回调在路由缺失时仍必须保留 Canonical 冲突事实；此时 project 尚不可权威确定，查询退回 tenant scope。
ALTER TABLE int_canonical_message
    ALTER COLUMN project_id DROP NOT NULL;

CREATE TABLE int_external_review_route (
    review_route_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    connector_version_id varchar(120) NOT NULL,
    external_order_code varchar(50) NOT NULL,
    review_case_id uuid NOT NULL,
    external_submission_ref varchar(160) NOT NULL,
    callback_batch_ref varchar(160) NOT NULL,
    mapping_version_id varchar(120) NOT NULL,
    status varchar(24) NOT NULL,
    canonical_message_id uuid,
    created_by varchar(160) NOT NULL,
    created_at timestamptz NOT NULL,
    completed_at timestamptz,
    CONSTRAINT fk_int_external_review_route_project
        FOREIGN KEY (project_id) REFERENCES prj_project(project_id),
    CONSTRAINT fk_int_external_review_route_canonical
        FOREIGN KEY (canonical_message_id) REFERENCES int_canonical_message(canonical_message_id),
    CONSTRAINT uq_int_external_review_route_case UNIQUE (tenant_id, review_case_id),
    CONSTRAINT ck_int_external_review_route_status CHECK (status IN ('ACTIVE', 'COMPLETED')),
    CONSTRAINT ck_int_external_review_route_completion CHECK (
        (status = 'ACTIVE' AND canonical_message_id IS NULL AND completed_at IS NULL)
        OR
        (status = 'COMPLETED' AND canonical_message_id IS NOT NULL AND completed_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_int_external_review_route_active_order
    ON int_external_review_route (tenant_id, connector_version_id, external_order_code)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_int_external_review_route_project_status
    ON int_external_review_route (tenant_id, project_id, status, created_at DESC);

CREATE TABLE int_inbound_item_result (
    inbound_envelope_id uuid NOT NULL,
    tenant_id varchar(64) NOT NULL,
    item_key varchar(160) NOT NULL,
    canonical_message_id uuid NOT NULL,
    processing_result varchar(24) NOT NULL,
    result_code varchar(80) NOT NULL,
    result_type varchar(80) NOT NULL,
    result_id varchar(160) NOT NULL,
    completed_at timestamptz NOT NULL,
    PRIMARY KEY (inbound_envelope_id, item_key),
    CONSTRAINT fk_int_inbound_item_envelope
        FOREIGN KEY (inbound_envelope_id) REFERENCES int_inbound_envelope(inbound_envelope_id),
    CONSTRAINT fk_int_inbound_item_canonical
        FOREIGN KEY (canonical_message_id) REFERENCES int_canonical_message(canonical_message_id),
    CONSTRAINT ck_int_inbound_item_result CHECK (processing_result IN ('ACCEPTED', 'REJECTED'))
);

CREATE INDEX ix_int_inbound_item_canonical
    ON int_inbound_item_result (tenant_id, canonical_message_id);

CREATE OR REPLACE FUNCTION int_guard_external_review_route_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'external review route is immutable';
    END IF;
    IF ROW(NEW.review_route_id, NEW.tenant_id, NEW.project_id,
           NEW.connector_version_id, NEW.external_order_code, NEW.review_case_id,
           NEW.external_submission_ref, NEW.callback_batch_ref, NEW.mapping_version_id,
           NEW.created_by, NEW.created_at)
       IS DISTINCT FROM
       ROW(OLD.review_route_id, OLD.tenant_id, OLD.project_id,
           OLD.connector_version_id, OLD.external_order_code, OLD.review_case_id,
           OLD.external_submission_ref, OLD.callback_batch_ref, OLD.mapping_version_id,
           OLD.created_by, OLD.created_at) THEN
        RAISE EXCEPTION 'external review route identity is immutable';
    END IF;
    IF OLD.status = 'COMPLETED'
       AND ROW(NEW.status, NEW.canonical_message_id, NEW.completed_at)
           IS DISTINCT FROM
           ROW(OLD.status, OLD.canonical_message_id, OLD.completed_at) THEN
        RAISE EXCEPTION 'completed external review route is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_int_external_review_route_immutable
    BEFORE UPDATE OR DELETE ON int_external_review_route
    FOR EACH ROW EXECUTE FUNCTION int_guard_external_review_route_mutation();

CREATE OR REPLACE FUNCTION int_reject_inbound_item_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'inbound item result is immutable';
END;
$$;

CREATE TRIGGER trg_int_inbound_item_immutable
    BEFORE UPDATE OR DELETE ON int_inbound_item_result
    FOR EACH ROW EXECUTE FUNCTION int_reject_inbound_item_mutation();

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('integration.registerExternalReviewRoute', '登记车企审核回调权威路由', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;

COMMENT ON COLUMN int_inbound_replay_guard.request_date_epoch_day IS
    'M57：V7.3.1 Cur_Time 业务日期的 LocalDate epochDay；旧秒值只按既有 expires_at 自然退出，不参与新请求匹配';
COMMENT ON TABLE int_external_review_route IS 'M57：外部订单到 OPEN CLIENT ReviewCase 的显式不可变路由';
COMMENT ON TABLE int_inbound_item_result IS 'M57：批量 Envelope 内逐业务项的不可变响应投影';
