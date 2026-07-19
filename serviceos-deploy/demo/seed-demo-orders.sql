-- ServiceOS 演示工单（本地/演示）。幂等，禁止用于生产。
-- 依赖 admin-pilot 项目与 Bundle 已存在。
\set ON_ERROR_STOP on

INSERT INTO wo_work_order (
    id, tenant_id, project_id, client_code, brand_code, service_product_code,
    external_order_code, payload_digest, status, configuration_bundle_id,
    configuration_bundle_code, configuration_bundle_version, configuration_bundle_digest,
    province_code, city_code, district_code, customer_name, customer_mobile,
    service_address, vehicle_vin, external_dispatched_at, received_at, activated_at, version
)
SELECT
    ('d3500000-0000-4000-8000-' || lpad(to_hex(g), 12, '0'))::uuid,
    'tenant-local',
    '10000000-0000-4000-8000-000000000001',
    'GEELY',
    'GALAXY',
    'HOME_CHARGING_SURVEY_INSTALL',
    'WO-DEMO-20260719-' || lpad(g::text, 3, '0'),
    repeat('e', 64),
    CASE WHEN g = 1 THEN 'RECEIVED' ELSE 'ACTIVE' END,
    '30000000-0000-4000-8000-000000000001',
    'ADMIN-PILOT-BUNDLE',
    '1.0.0',
    repeat('c', 64),
    '370000',
    '370100',
    '370102',
    '王先生',
    '13800000001',
    '【演示数据】山东省济南市历下区演示路 88 号',
    'DEMOVIN000000000' || lpad(g::text, 2, '0'),
    localtimestamp - ((20 - g) || ' hours')::interval,
    now() - ((20 - g) || ' hours')::interval,
    CASE WHEN g = 1 THEN NULL ELSE now() - ((19 - g) || ' hours')::interval END,
    1
FROM generate_series(1, 20) AS g
ON CONFLICT (id) DO UPDATE
SET external_order_code = EXCLUDED.external_order_code,
    status = EXCLUDED.status,
    customer_name = EXCLUDED.customer_name,
    customer_mobile = EXCLUDED.customer_mobile,
    service_address = EXCLUDED.service_address,
    client_code = EXCLUDED.client_code,
    brand_code = EXCLUDED.brand_code,
    activated_at = EXCLUDED.activated_at;
