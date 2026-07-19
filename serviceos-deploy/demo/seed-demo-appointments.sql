-- 为 WO-DEMO-007/008 写入 CONFIRMED 勘测预约，供网点联系/预约与师傅签到演练。
-- 依赖 seed-demo-tasks.sql（任务与网点/师傅责任已存在）。
\set ON_ERROR_STOP on

CREATE TEMP TABLE demo_appt (
    g int PRIMARY KEY,
    task_id uuid NOT NULL,
    work_order_id uuid NOT NULL,
    appointment_id uuid NOT NULL,
    revision_id uuid NOT NULL,
    history_id uuid NOT NULL
);

INSERT INTO demo_appt VALUES
(
    7,
    'd3500000-2000-4000-8000-000000000007',
    'd3500000-0000-4000-8000-000000000007',
    'd3500000-2a00-4000-8000-000000000007',
    'd3500000-2a10-4000-8000-000000000007',
    'd3500000-2a20-4000-8000-000000000007'
),
(
    8,
    'd3500000-2000-4000-8000-000000000008',
    'd3500000-0000-4000-8000-000000000008',
    'd3500000-2a00-4000-8000-000000000008',
    'd3500000-2a10-4000-8000-000000000008',
    'd3500000-2a20-4000-8000-000000000008'
);

-- current_revision FK 可延迟校验
BEGIN;

INSERT INTO apt_appointment (
    appointment_id, tenant_id, project_id, work_order_id, task_id,
    appointment_type, status, current_revision_id, current_revision_no,
    assigned_network_id, technician_id, aggregate_version, created_by, created_at
)
SELECT
    appointment_id,
    'tenant-local',
    '10000000-0000-4000-8000-000000000001',
    work_order_id,
    task_id,
    'SURVEY',
    'CONFIRMED',
    revision_id,
    1,
    'd3500000-1000-4000-8000-000000000002',
    'd3500000-1000-4000-8000-000000000004',
    1,
    'demo-fixture',
    now() - interval '6 hours'
FROM demo_appt
ON CONFLICT (appointment_id) DO UPDATE
SET status = EXCLUDED.status,
    assigned_network_id = EXCLUDED.assigned_network_id,
    technician_id = EXCLUDED.technician_id;

INSERT INTO apt_appointment_revision (
    revision_id, tenant_id, appointment_id, revision_no, revision_kind,
    window_start, window_end, timezone, estimated_duration_minutes,
    address_ref, address_version,
    confirmed_party_type, confirmed_party_ref, confirmation_channel, confirmed_at,
    created_by, created_at
)
SELECT
    revision_id,
    'tenant-local',
    appointment_id,
    1,
    'CONFIRM',
    now() + interval '2 hours',
    now() + interval '4 hours',
    'Asia/Shanghai',
    120,
    'demo://address/wo-demo-' || lpad(g::text, 3, '0'),
    'demo-v1',
    'NETWORK_MEMBER',
    '06b612f3-a901-4b0e-bd90-86b4259cc087',
    'PHONE',
    now() - interval '5 hours',
    'demo-fixture',
    now() - interval '5 hours'
FROM demo_appt
ON CONFLICT (revision_id) DO NOTHING;

INSERT INTO apt_appointment_status_history (
    history_id, tenant_id, appointment_id, aggregate_version,
    from_status, to_status, command_code, actor_id, revision_id, occurred_at
)
SELECT
    history_id,
    'tenant-local',
    appointment_id,
    1,
    NULL,
    'CONFIRMED',
    'CONFIRM',
    'demo-fixture',
    revision_id,
    now() - interval '5 hours'
FROM demo_appt
ON CONFLICT (history_id) DO NOTHING;

COMMIT;

DROP TABLE demo_appt;
