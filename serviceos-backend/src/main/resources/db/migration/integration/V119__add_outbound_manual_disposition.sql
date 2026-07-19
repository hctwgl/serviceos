-- M318：UNKNOWN OutboundDelivery 的人工确认/放弃处置。
-- Delivery 状态保持 UNKNOWN（不得伪装 ACKNOWLEDGED / 技术成功）；
-- 以不可变 disposition 记录业务处置并触发运营异常闭环。

CREATE TABLE int_delivery_manual_disposition (
    disposition_id uuid PRIMARY KEY,
    delivery_id uuid NOT NULL,
    tenant_id varchar(64) NOT NULL,
    expected_delivery_version bigint NOT NULL,
    result varchar(32) NOT NULL,
    reason varchar(1000) NOT NULL,
    approval_ref varchar(160) NOT NULL,
    external_ref varchar(200),
    evidence_refs_json text NOT NULL,
    requested_by varchar(160) NOT NULL,
    requested_at timestamptz NOT NULL,
    CONSTRAINT fk_int_delivery_manual_disposition_delivery
        FOREIGN KEY (delivery_id) REFERENCES int_outbound_delivery(delivery_id),
    CONSTRAINT uq_int_delivery_manual_disposition_delivery UNIQUE (delivery_id),
    CONSTRAINT ck_int_delivery_manual_disposition_version CHECK (expected_delivery_version > 0),
    CONSTRAINT ck_int_delivery_manual_disposition_result CHECK (
        result IN ('MANUAL_CONFIRMED', 'ABANDONED'))
);

CREATE INDEX ix_int_delivery_manual_disposition_tenant
    ON int_delivery_manual_disposition (tenant_id, requested_at DESC);

CREATE OR REPLACE FUNCTION int_reject_manual_disposition_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'delivery manual disposition is immutable';
END;
$$;

CREATE TRIGGER trg_int_delivery_manual_disposition_immutable
    BEFORE UPDATE OR DELETE ON int_delivery_manual_disposition
    FOR EACH ROW EXECUTE FUNCTION int_reject_manual_disposition_mutation();

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('integration.recordManualOutboundAck', '人工确认或放弃未知结果的外部交付', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;

COMMENT ON TABLE int_delivery_manual_disposition IS
    'M318：UNKNOWN Delivery 的不可变人工确认/放弃处置；Delivery 状态保持 UNKNOWN，不创建 CLIENT Case/Route';
