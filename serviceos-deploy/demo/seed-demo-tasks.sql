-- 为 WO-DEMO-* 补齐可演练 HUMAN 任务、网点/师傅责任与 SLA/异常投影。
-- 幂等；禁止用于生产。依赖：seed-demo-orders.sql + seed-demo-network-portal.sql + admin-pilot Bundle。
--
-- 说明（诚实边界）：
-- - 场景通过「任务状态 + 责任分配 + stage + SLA/异常」表达，供列表/工作台/接单指派演练；
-- - 不伪造完整审核/整改/预约领域对象（缺证据快照等真实前置时禁止灌库）；
-- - 审核/整改/预约写链路仍须走真实命令，不得把本种子当作全流程后端完成证明。
\set ON_ERROR_STOP on

-- 固定演示主体
-- 项目 / Bundle / 工作流定义：沿用 admin-pilot
-- 网点：d3500000-1000-4000-8000-000000000002
-- 张师傅档案：d3500000-1000-4000-8000-000000000004
-- developer：06b612f3-a901-4b0e-bd90-86b4259cc087

CREATE TEMP TABLE demo_scenario (
    g int PRIMARY KEY,
    scenario_label text NOT NULL,
    wo_status text NOT NULL,
    task_status text NOT NULL,
    stage_code text NOT NULL,
    task_type text NOT NULL,
    need_network boolean NOT NULL,
    need_technician boolean NOT NULL,
    claimed boolean NOT NULL,
    running boolean NOT NULL,
    completed boolean NOT NULL,
    cancelled boolean NOT NULL,
    sla_mode text NOT NULL, -- NONE | SOON | BREACHED
    with_exception boolean NOT NULL
);

INSERT INTO demo_scenario VALUES
(1,  '待初审',           'RECEIVED',  'READY',     'PILOT_SURVEY', 'PILOT_SURVEY', false, false, false, false, false, false, 'NONE',     false),
(2,  '待分配网点',       'ACTIVE',    'READY',     'PILOT_SURVEY', 'PILOT_SURVEY', false, false, false, false, false, false, 'NONE',     false),
(3,  '网点待接单',       'ACTIVE',    'READY',     'PILOT_SURVEY', 'PILOT_SURVEY', false, false, false, false, false, false, 'NONE',     false),
(4,  '待指派师傅',       'ACTIVE',    'READY',     'PILOT_SURVEY', 'PILOT_SURVEY', true,  false, false, false, false, false, 'NONE',     false),
(5,  '待联系客户',       'ACTIVE',    'READY',     'PILOT_SURVEY', 'PILOT_SURVEY', true,  true,  false, false, false, false, 'NONE',     false),
(6,  '待预约',           'ACTIVE',    'CLAIMED',   'PILOT_SURVEY', 'PILOT_SURVEY', true,  true,  true,  false, false, false, 'NONE',     false),
(7,  '待上门',           'ACTIVE',    'CLAIMED',   'PILOT_SURVEY', 'PILOT_SURVEY', true,  true,  true,  false, false, false, 'NONE',     false),
(8,  '勘测中',           'ACTIVE',    'RUNNING',   'PILOT_SURVEY', 'PILOT_SURVEY', true,  true,  true,  true,  false, false, 'NONE',     false),
(9,  '待审核勘测资料',   'ACTIVE',    'COMPLETED', 'PILOT_SURVEY', 'PILOT_SURVEY', true,  true,  true,  true,  true,  false, 'NONE',     false),
(10, '待安装',           'ACTIVE',    'READY',     'INSTALLATION', 'PILOT_COMPLETION', true, true, false, false, false, false, 'NONE',  false),
(11, '安装中',           'ACTIVE',    'RUNNING',   'INSTALLATION', 'PILOT_COMPLETION', true, true, true,  true,  false, false, 'NONE',  false),
(12, '待提交完工资料',   'ACTIVE',    'RUNNING',   'INSTALLATION', 'PILOT_COMPLETION', true, true, true,  true,  false, false, 'NONE',  false),
(13, '待审核完工资料',   'ACTIVE',    'COMPLETED', 'INSTALLATION', 'PILOT_COMPLETION', true, true, true,  true,  true,  false, 'NONE',  false),
(14, '整改中',           'ACTIVE',    'READY',     'CORRECTION',   'PILOT_SURVEY', true,  true,  false, false, false, false, 'NONE',     false),
(15, '已重新提交',       'ACTIVE',    'COMPLETED', 'CORRECTION',   'PILOT_SURVEY', true,  true,  true,  true,  true,  false, 'NONE',     false),
(16, '已完成',           'FULFILLED', 'COMPLETED', 'INSTALLATION', 'PILOT_COMPLETION', true, true, true,  true,  true,  false, 'NONE',  false),
(17, '已取消',           'CANCELLED', 'CANCELLED', 'PILOT_SURVEY', 'PILOT_SURVEY', false, false, false, false, false, true,  'NONE',     false),
(18, 'SLA即将超时',      'ACTIVE',    'RUNNING',   'PILOT_SURVEY', 'PILOT_SURVEY', true,  true,  true,  true,  false, false, 'SOON',     false),
(19, 'SLA已超时',        'ACTIVE',    'RUNNING',   'PILOT_SURVEY', 'PILOT_SURVEY', true,  true,  true,  true,  false, false, 'BREACHED', false),
(20, '运营异常',         'ACTIVE',    'READY',     'PILOT_SURVEY', 'PILOT_SURVEY', true,  false, false, false, false, false, 'NONE',     true);

-- 更新工单状态与场景标签（便于业务人员在列表识别）
UPDATE wo_work_order w
SET status = s.wo_status,
    customer_name = '王先生·' || s.scenario_label,
    activated_at = CASE
        WHEN s.wo_status = 'RECEIVED' THEN NULL
        WHEN s.wo_status = 'CANCELLED' AND w.activated_at IS NULL THEN NULL
        ELSE COALESCE(w.activated_at, now() - ((21 - s.g) || ' hours')::interval)
    END,
    cancelled_at = CASE
        WHEN s.wo_status = 'CANCELLED' THEN COALESCE(w.cancelled_at, now() - interval '4 hours')
        ELSE NULL
    END,
    cancel_reason_code = CASE
        WHEN s.wo_status = 'CANCELLED' THEN COALESCE(w.cancel_reason_code, 'DEMO_CANCELLED')
        ELSE NULL
    END,
    fulfilled_at = CASE
        WHEN s.wo_status = 'FULFILLED' THEN COALESCE(w.fulfilled_at, now() - interval '2 hours')
        ELSE NULL
    END
FROM demo_scenario s
WHERE w.tenant_id = 'tenant-local'
  AND w.id = ('d3500000-0000-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid;

-- 工作流实例（每单一个 ROOT）
INSERT INTO wfl_workflow_instance (
    workflow_instance_id, tenant_id, project_id, work_order_id, configuration_bundle_id,
    workflow_definition_version_id, workflow_key, workflow_version, definition_digest,
    status, start_event_id, correlation_id, version, started_at, completed_at,
    configuration_bundle_digest
)
SELECT
    ('d3500000-2100-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    'tenant-local',
    '10000000-0000-4000-8000-000000000001',
    ('d3500000-0000-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    '30000000-0000-4000-8000-000000000001',
    '20000000-0000-4000-8000-000000000001',
    'ADMIN_PILOT', '1.0.0', repeat('a', 64),
    CASE
        WHEN s.wo_status = 'FULFILLED' THEN 'COMPLETED'
        WHEN s.wo_status = 'CANCELLED' THEN 'CANCELLED'
        ELSE 'ACTIVE'
    END,
    ('d3500000-2110-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    'demo-seed-' || s.g::text,
    1,
    now() - ((21 - s.g) || ' hours')::interval,
    CASE
        WHEN s.wo_status IN ('FULFILLED', 'CANCELLED') THEN now() - interval '1 hour'
        ELSE NULL
    END,
    repeat('c', 64)
FROM demo_scenario s
ON CONFLICT (workflow_instance_id) DO UPDATE
SET status = EXCLUDED.status,
    started_at = EXCLUDED.started_at,
    completed_at = EXCLUDED.completed_at;

INSERT INTO wfl_stage_instance (
    stage_instance_id, tenant_id, workflow_instance_id, work_order_id, stage_code,
    sequence_no, status, activation_event_id, version, activated_at, completed_at
)
SELECT
    ('d3500000-2200-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    'tenant-local',
    ('d3500000-2100-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    ('d3500000-0000-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    s.stage_code,
    1,
    CASE
        WHEN s.wo_status = 'CANCELLED' THEN 'CANCELLED'
        WHEN s.completed OR s.wo_status = 'FULFILLED' THEN 'COMPLETED'
        ELSE 'ACTIVE'
    END,
    ('d3500000-2210-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    1,
    now() - ((20 - s.g) || ' hours')::interval,
    CASE
        WHEN s.completed OR s.wo_status IN ('FULFILLED', 'CANCELLED') THEN now() - interval '1 hour'
        ELSE NULL
    END
FROM demo_scenario s
ON CONFLICT (stage_instance_id) DO UPDATE
SET stage_code = EXCLUDED.stage_code,
    status = EXCLUDED.status,
    completed_at = EXCLUDED.completed_at;

-- HUMAN 任务
INSERT INTO tsk_task (
    task_id, tenant_id, task_type, task_kind, business_key, payload_digest, priority,
    status, next_run_at, attempt_count, max_attempts, correlation_id, version,
    created_at, updated_at, completed_at, project_id, work_order_id, workflow_instance_id,
    stage_instance_id, workflow_node_instance_id, workflow_node_id,
    workflow_definition_version_id, workflow_definition_digest, configuration_bundle_id,
    configuration_bundle_digest, stage_code, sla_ref,
    claimed_by, claimed_at, started_at, result_ref, result_digest,
    cancelled_at, cancellation_reason_code, cancellation_source_event_id
)
SELECT
    ('d3500000-2000-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    'tenant-local',
    s.task_type,
    'HUMAN',
    'demo:' || s.g::text || ':' || s.scenario_label,
    repeat('e', 64),
    500,
    s.task_status,
    now() - ((19 - s.g) || ' hours')::interval,
    0, 3,
    'demo-seed-' || s.g::text,
    1,
    now() - ((19 - s.g) || ' hours')::interval,
    now() - ((18 - s.g) || ' hours')::interval,
    CASE WHEN s.completed THEN now() - ((10 - least(s.g, 9)) || ' hours')::interval ELSE NULL END,
    '10000000-0000-4000-8000-000000000001',
    ('d3500000-0000-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    ('d3500000-2100-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    ('d3500000-2200-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    ('d3500000-2300-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    s.stage_code || '_NODE',
    '20000000-0000-4000-8000-000000000001',
    repeat('a', 64),
    '30000000-0000-4000-8000-000000000001',
    repeat('c', 64),
    s.stage_code,
    -- SLA 实例触发器要求 task.sla_ref 与 instance.sla_ref 一致，且 Bundle 含该 SLA 资产
    CASE WHEN s.sla_mode IN ('SOON', 'BREACHED') THEN 'PILOT_RESPONSE' ELSE NULL END,
    CASE WHEN s.claimed OR s.running OR s.completed THEN '06b612f3-a901-4b0e-bd90-86b4259cc087' ELSE NULL END,
    CASE WHEN s.claimed OR s.running OR s.completed THEN now() - interval '8 hours' ELSE NULL END,
    CASE WHEN s.running OR s.completed THEN now() - interval '6 hours' ELSE NULL END,
    CASE WHEN s.completed THEN 'demo://result/' || s.g::text ELSE NULL END,
    CASE WHEN s.completed THEN repeat('b', 64) ELSE NULL END,
    CASE WHEN s.cancelled THEN now() - interval '4 hours' ELSE NULL END,
    CASE WHEN s.cancelled THEN 'DEMO_CANCELLED' ELSE NULL END,
    CASE WHEN s.cancelled THEN ('d3500000-2f00-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid ELSE NULL END
FROM demo_scenario s
ON CONFLICT (task_id) DO UPDATE
SET status = EXCLUDED.status,
    task_type = EXCLUDED.task_type,
    stage_code = EXCLUDED.stage_code,
    sla_ref = EXCLUDED.sla_ref,
    claimed_by = EXCLUDED.claimed_by,
    claimed_at = EXCLUDED.claimed_at,
    started_at = EXCLUDED.started_at,
    completed_at = EXCLUDED.completed_at,
    result_ref = EXCLUDED.result_ref,
    result_digest = EXCLUDED.result_digest,
    cancelled_at = EXCLUDED.cancelled_at,
    cancellation_reason_code = EXCLUDED.cancellation_reason_code,
    cancellation_source_event_id = EXCLUDED.cancellation_source_event_id,
    updated_at = now();

-- 产能计数器（演示网点 / 张师傅）
INSERT INTO dsp_capacity_counter (
    capacity_counter_id, tenant_id, responsibility_level, assignee_id,
    business_type, max_units, occupied_units, version, updated_by, updated_at
) VALUES
(
    'd3500000-2700-4000-8000-000000000001', 'tenant-local', 'NETWORK',
    'd3500000-1000-4000-8000-000000000002', 'INSTALLATION', 100, 0, 1, 'demo-fixture', now()
),
(
    'd3500000-2700-4000-8000-000000000002', 'tenant-local', 'TECHNICIAN',
    'd3500000-1000-4000-8000-000000000004', 'INSTALLATION', 100, 0, 1, 'demo-fixture', now()
)
ON CONFLICT (tenant_id, responsibility_level, assignee_id, business_type) DO NOTHING;

-- 网点 ACTIVE 责任
INSERT INTO dsp_service_assignment (
    service_assignment_id, tenant_id, work_order_id, task_id,
    responsibility_level, assignee_id, business_type, source_decision_id,
    status, activation_saga_id, effective_from, created_by, created_at,
    authority_assignment_id, authority_version, fence_decision_id, fence_policy_version
)
SELECT
    ('d3500000-2400-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    'tenant-local',
    ('d3500000-0000-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    ('d3500000-2000-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    'NETWORK',
    'd3500000-1000-4000-8000-000000000002',
    'INSTALLATION',
    'demo://network-decision/' || s.g::text,
    'ACTIVE',
    ('d3500000-2410-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    now() - interval '12 hours',
    'demo-fixture',
    now() - interval '12 hours',
    'demo://network-authority/' || s.g::text,
    1,
    'demo://network-fence/' || s.g::text,
    'demo-fence-v1'
FROM demo_scenario s
WHERE s.need_network
ON CONFLICT (service_assignment_id) DO NOTHING;

INSERT INTO dsp_capacity_reservation (
    capacity_reservation_id, tenant_id, service_assignment_id, capacity_counter_id,
    units, status, held_at, confirmed_at
)
SELECT
    ('d3500000-2420-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    'tenant-local',
    ('d3500000-2400-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    c.capacity_counter_id,
    1, 'CONFIRMED', now() - interval '12 hours', now() - interval '12 hours'
FROM demo_scenario s
JOIN dsp_capacity_counter c
  ON c.tenant_id = 'tenant-local'
 AND c.responsibility_level = 'NETWORK'
 AND c.assignee_id = 'd3500000-1000-4000-8000-000000000002'
 AND c.business_type = 'INSTALLATION'
WHERE s.need_network
ON CONFLICT (capacity_reservation_id) DO NOTHING;

-- 师傅 ACTIVE 责任
INSERT INTO dsp_service_assignment (
    service_assignment_id, tenant_id, work_order_id, task_id,
    responsibility_level, assignee_id, business_type, source_decision_id,
    status, activation_saga_id, effective_from, created_by, created_at,
    authority_assignment_id, authority_version, fence_decision_id, fence_policy_version
)
SELECT
    ('d3500000-2500-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    'tenant-local',
    ('d3500000-0000-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    ('d3500000-2000-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    'TECHNICIAN',
    'd3500000-1000-4000-8000-000000000004',
    'INSTALLATION',
    'demo://tech-decision/' || s.g::text,
    'ACTIVE',
    ('d3500000-2510-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    now() - interval '10 hours',
    'demo-fixture',
    now() - interval '10 hours',
    'demo://tech-authority/' || s.g::text,
    1,
    'demo://tech-fence/' || s.g::text,
    'demo-fence-v1'
FROM demo_scenario s
WHERE s.need_technician
ON CONFLICT (service_assignment_id) DO NOTHING;

INSERT INTO dsp_capacity_reservation (
    capacity_reservation_id, tenant_id, service_assignment_id, capacity_counter_id,
    units, status, held_at, confirmed_at
)
SELECT
    ('d3500000-2520-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    'tenant-local',
    ('d3500000-2500-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    c.capacity_counter_id,
    1, 'CONFIRMED', now() - interval '10 hours', now() - interval '10 hours'
FROM demo_scenario s
JOIN dsp_capacity_counter c
  ON c.tenant_id = 'tenant-local'
 AND c.responsibility_level = 'TECHNICIAN'
 AND c.assignee_id = 'd3500000-1000-4000-8000-000000000004'
 AND c.business_type = 'INSTALLATION'
WHERE s.need_technician
ON CONFLICT (capacity_reservation_id) DO NOTHING;

-- SLA：即将超时 / 已超时
INSERT INTO sla_instance (
    sla_instance_id, tenant_id, project_id, work_order_id, task_id, sla_ref,
    policy_version_id, policy_semantic_version, policy_content_digest, clock_mode,
    target_duration_seconds, start_event_id, started_at, deadline_at, status,
    breached_at, breach_detected_at,
    aggregate_version, correlation_id, created_at, updated_at
)
SELECT
    ('d3500000-2600-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    'tenant-local',
    '10000000-0000-4000-8000-000000000001',
    ('d3500000-0000-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    ('d3500000-2000-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    'PILOT_RESPONSE',
    '20000000-0000-4000-8000-000000000002',
    '1.0.0',
    -- 必须与 admin-pilot Bundle 内 SLA 资产 content_digest 一致（触发器校验）
    '3ca27e3a4ce99cf98cacf1d2c8203f5aa353cc86cfa96749bd8595803cc1ceb5',
    'ELAPSED',
    CASE WHEN s.sla_mode = 'SOON' THEN 7200 ELSE 3600 END,
    ('d3500000-2610-4000-8000-' || lpad(to_hex(s.g), 12, '0'))::uuid,
    CASE WHEN s.sla_mode = 'SOON' THEN now() - interval '90 minutes' ELSE now() - interval '3 hours' END,
    CASE WHEN s.sla_mode = 'SOON' THEN now() + interval '30 minutes' ELSE now() - interval '1 hour' END,
    CASE WHEN s.sla_mode = 'SOON' THEN 'RUNNING' ELSE 'BREACHED' END,
    CASE WHEN s.sla_mode = 'BREACHED' THEN now() - interval '1 hour' ELSE NULL END,
    CASE WHEN s.sla_mode = 'BREACHED' THEN now() - interval '55 minutes' ELSE NULL END,
    1,
    'demo-sla-' || s.g::text,
    now(), now()
FROM demo_scenario s
WHERE s.sla_mode IN ('SOON', 'BREACHED')
ON CONFLICT (sla_instance_id) DO UPDATE
SET status = EXCLUDED.status,
    deadline_at = EXCLUDED.deadline_at,
    breached_at = EXCLUDED.breached_at,
    breach_detected_at = EXCLUDED.breach_detected_at,
    updated_at = now();

-- 运营异常（绑定第 20 号演示单）
INSERT INTO ops_operational_exception (
    exception_id, tenant_id, source_type, source_id, source_attempt_id, source_task_type,
    category_code, severity_code, error_code, status, correlation_id,
    opened_at, work_order_id, task_id, occurrence_count, last_detected_at,
    aggregate_version, project_id
)
SELECT
    'd3500000-2800-4000-8000-000000000014',
    'tenant-local',
    'TASK',
    ('d3500000-2000-4000-8000-' || lpad(to_hex(20), 12, '0'))::text,
    'd3500000-2810-4000-8000-000000000014',
    'PILOT_SURVEY',
    'FULFILLMENT',
    'P1',
    'DEMO_OPS_EXCEPTION',
    'OPEN',
    'demo-exception-20',
    now() - interval '45 minutes',
    'd3500000-0000-4000-8000-000000000014',
    'd3500000-2000-4000-8000-000000000014',
    1,
    now() - interval '45 minutes',
    1,
    '10000000-0000-4000-8000-000000000001'
WHERE EXISTS (SELECT 1 FROM demo_scenario WHERE g = 20 AND with_exception)
ON CONFLICT (exception_id) DO NOTHING;

DROP TABLE demo_scenario;
