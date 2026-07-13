-- Flyway 会先执行所有 versioned migration，再执行 repeatable migration。
-- 因此 V014 必须既能在空库创建最终结构，也能升级此前由 R__work_order_core.sql 创建的空参考表。
CREATE TABLE IF NOT EXISTS wo_work_order (
    id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    client_code varchar(64) NOT NULL,
    brand_code varchar(64) NOT NULL,
    service_product_code varchar(96) NOT NULL,
    external_order_code varchar(128) NOT NULL,
    payload_digest char(64) NOT NULL,
    status varchar(32) NOT NULL,
    configuration_bundle_id uuid NOT NULL,
    configuration_bundle_code varchar(128) NOT NULL,
    configuration_bundle_version varchar(64) NOT NULL,
    province_code varchar(16) NOT NULL,
    city_code varchar(16) NOT NULL,
    district_code varchar(16) NOT NULL,
    customer_name varchar(128) NOT NULL,
    customer_mobile varchar(32) NOT NULL,
    service_address varchar(512) NOT NULL,
    vehicle_vin varchar(32) NOT NULL,
    external_dispatched_at timestamp without time zone NOT NULL,
    received_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 1,
    CONSTRAINT uq_wo_external_order
        UNIQUE (tenant_id, client_code, external_order_code),
    CONSTRAINT fk_wo_configuration_scope
        FOREIGN KEY (tenant_id, project_id, configuration_bundle_id)
        REFERENCES cfg_configuration_bundle(tenant_id, project_id, bundle_id),
    CONSTRAINT ck_wo_payload_digest CHECK (payload_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_wo_version CHECK (version >= 1)
);

-- 已有旧结构无法可靠推断 tenant/project/bundle，存在数据时失败关闭，不写伪造归属。
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = current_schema()
           AND table_name = 'wo_work_order'
           AND column_name = 'tenant_id'
    ) AND EXISTS (SELECT 1 FROM wo_work_order LIMIT 1) THEN
        RAISE EXCEPTION 'V014 requires an explicit tenant/project backfill for existing work orders';
    END IF;
END;
$$;

ALTER TABLE wo_work_order
    ADD COLUMN IF NOT EXISTS tenant_id varchar(64),
    ADD COLUMN IF NOT EXISTS project_id uuid,
    ADD COLUMN IF NOT EXISTS configuration_bundle_id uuid;

ALTER TABLE wo_work_order
    ALTER COLUMN tenant_id SET NOT NULL,
    ALTER COLUMN project_id SET NOT NULL,
    ALTER COLUMN configuration_bundle_id SET NOT NULL;

ALTER TABLE wo_work_order DROP CONSTRAINT IF EXISTS uq_wo_external_order;
ALTER TABLE wo_work_order DROP CONSTRAINT IF EXISTS fk_wo_configuration_scope;
ALTER TABLE wo_work_order
    ADD CONSTRAINT uq_wo_external_order
        UNIQUE (tenant_id, client_code, external_order_code),
    ADD CONSTRAINT fk_wo_configuration_scope
        FOREIGN KEY (tenant_id, project_id, configuration_bundle_id)
        REFERENCES cfg_configuration_bundle(tenant_id, project_id, bundle_id);

CREATE INDEX IF NOT EXISTS ix_wo_work_order_status_received
    ON wo_work_order (status, received_at);

CREATE INDEX IF NOT EXISTS ix_wo_work_order_region
    ON wo_work_order (province_code, city_code, district_code);

CREATE INDEX IF NOT EXISTS ix_wo_work_order_project_status
    ON wo_work_order (tenant_id, project_id, status, received_at);
