-- 为演示场景 009 / 013 种子 OPEN 审核单（真实 FK 链），供管理端 decide REJECTED → 自动开整改。
-- 依赖：seed-demo-tasks.sql（对应 HUMAN 任务已存在）。
-- 明确不做：伪造网点复核写链路；不预置 OPEN CorrectionCase（须经真实驳回以带 correction Task）。
\set ON_ERROR_STOP on

-- g=9 待审核勘测资料；g=13 待审核完工资料
WITH targets(g, stage_code) AS (
    VALUES
        (9, 'PILOT_SURVEY'),
        (13, 'INSTALLATION')
),
ids AS (
    SELECT
        g,
        stage_code,
        ('d3500000-0000-4000-8000-' || lpad(to_hex(g), 12, '0'))::uuid AS work_order_id,
        ('d3500000-2000-4000-8000-' || lpad(to_hex(g), 12, '0'))::uuid AS task_id,
        ('d3500000-2900-4000-8000-' || lpad(to_hex(g), 12, '0'))::uuid AS resolution_id,
        ('d3500000-2910-4000-8000-' || lpad(to_hex(g), 12, '0'))::uuid AS snapshot_id,
        ('d3500000-2920-4000-8000-' || lpad(to_hex(g), 12, '0'))::uuid AS review_case_id,
        ('d3500000-2930-4000-8000-' || lpad(to_hex(g), 12, '0'))::uuid AS source_event_id,
        lpad(to_hex(g), 64, 'a') AS event_digest,
        lpad(to_hex(g + 100), 64, 'b') AS snapshot_digest
    FROM targets
)
INSERT INTO evd_task_evidence_resolution (
    resolution_id, tenant_id, project_id, task_id, configuration_bundle_id,
    configuration_bundle_digest, stage_code, source_event_id, source_event_digest,
    resolver_version, condition_input_digest, resolution_explanation,
    generation_no, condition_fact_type, condition_fact_ref, condition_fact_revision,
    slot_count, resolved_at
)
SELECT
    resolution_id,
    'tenant-local',
    '10000000-0000-4000-8000-000000000001',
    task_id,
    '30000000-0000-4000-8000-000000000001',
    repeat('c', 64),
    stage_code,
    source_event_id,
    event_digest,
    'FIXED_EVIDENCE_V1',
    '44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a',
    '{"kind":"DEMO_FIXED_CONTEXT"}'::jsonb,
    1,
    'TASK_CREATED',
    source_event_id::text,
    0,
    0,
    now() - interval '3 hours'
FROM ids
ON CONFLICT (resolution_id) DO NOTHING;

WITH targets(g) AS (VALUES (9), (13)),
ids AS (
    SELECT
        g,
        ('d3500000-2000-4000-8000-' || lpad(to_hex(g), 12, '0'))::uuid AS task_id,
        ('d3500000-2900-4000-8000-' || lpad(to_hex(g), 12, '0'))::uuid AS resolution_id,
        ('d3500000-2910-4000-8000-' || lpad(to_hex(g), 12, '0'))::uuid AS snapshot_id,
        lpad(to_hex(g + 100), 64, 'b') AS snapshot_digest
    FROM targets
)
INSERT INTO evd_evidence_set_snapshot (
    evidence_set_snapshot_id, tenant_id, project_id, task_id, resolution_id,
    purpose, member_count, content_digest, eligibility_summary, created_by, created_at
)
SELECT
    snapshot_id,
    'tenant-local',
    '10000000-0000-4000-8000-000000000001',
    task_id,
    resolution_id,
    'TASK_SUBMISSION',
    0,
    snapshot_digest,
    '{}'::jsonb,
    'demo-fixture',
    now() - interval '2 hours'
FROM ids
ON CONFLICT (evidence_set_snapshot_id) DO NOTHING;

WITH targets(g) AS (VALUES (9), (13)),
ids AS (
    SELECT
        g,
        ('d3500000-2000-4000-8000-' || lpad(to_hex(g), 12, '0'))::uuid AS task_id,
        ('d3500000-2910-4000-8000-' || lpad(to_hex(g), 12, '0'))::uuid AS snapshot_id,
        ('d3500000-2920-4000-8000-' || lpad(to_hex(g), 12, '0'))::uuid AS review_case_id,
        lpad(to_hex(g + 100), 64, 'b') AS snapshot_digest
    FROM targets
)
INSERT INTO evd_review_case (
    review_case_id, tenant_id, project_id, task_id, evidence_set_snapshot_id,
    snapshot_content_digest, scope_type, origin, policy_version, status,
    created_by, created_at, decided_at
)
SELECT
    review_case_id,
    'tenant-local',
    '10000000-0000-4000-8000-000000000001',
    task_id,
    snapshot_id,
    snapshot_digest,
    'EVIDENCE_SET_SNAPSHOT',
    'INTERNAL',
    'DEMO_POLICY_V1',
    'OPEN',
    'demo-fixture',
    now() - interval '90 minutes',
    NULL
FROM ids
ON CONFLICT (review_case_id) DO NOTHING;
