CREATE TABLE IF NOT EXISTS wo_work_order (
    id uuid PRIMARY KEY,
    client_code varchar(64) NOT NULL,
    brand_code varchar(64) NOT NULL,
    service_product_code varchar(96) NOT NULL,
    external_order_code varchar(128) NOT NULL,
    payload_digest char(64) NOT NULL,
    status varchar(32) NOT NULL,
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
    CONSTRAINT uq_wo_external_order UNIQUE (client_code, external_order_code),
    CONSTRAINT ck_wo_payload_digest CHECK (payload_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_wo_version CHECK (version >= 1)
);

CREATE INDEX IF NOT EXISTS ix_wo_work_order_status_received
    ON wo_work_order (status, received_at);

CREATE INDEX IF NOT EXISTS ix_wo_work_order_region
    ON wo_work_order (province_code, city_code, district_code);
