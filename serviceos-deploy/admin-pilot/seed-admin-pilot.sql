-- M134 Admin 试点局部读写冒烟夹具。全部标识固定且写入幂等，禁止复制到生产数据库。
\set ON_ERROR_STOP on

INSERT INTO prj_project (
    project_id, tenant_id, project_code, client_id, project_name, starts_on,
    project_status, aggregate_version, created_at
) VALUES (
    '10000000-0000-4000-8000-000000000001', 'tenant-local', 'ADMIN-PILOT',
    'BYD', 'Admin 试点项目', current_date, 'ACTIVE', 1, now()
) ON CONFLICT DO NOTHING;

-- M140/M141：WORKFLOW 必须可被 WorkflowDefinitionParser 解析，使入站 workorder.received
-- 经 Outbox 自动启动 Stage/Task 并激活工单；USER_TASK 带 formRef/slaRef，Bundle 含可启动
-- TaskSlaPolicy 的 SLA、以及 stage=PILOT_SURVEY 的 FORM/EVIDENCE，以支持同单表单/资料/审核/外发/完结。
-- 本地试点库若已写入旧定义，需短暂关闭不可变触发器才能替换（仅限本地夹具，禁止生产用法）。
ALTER TABLE cfg_configuration_asset_version DISABLE TRIGGER trg_cfg_asset_version_immutable;
ALTER TABLE cfg_configuration_bundle_item DISABLE TRIGGER trg_cfg_bundle_item_immutable;

INSERT INTO cfg_configuration_asset_version (
    version_id, tenant_id, asset_type, asset_key, semantic_version, schema_version,
    definition, content_digest, status, published_at
) VALUES
(
    '20000000-0000-4000-8000-000000000001', 'tenant-local', 'WORKFLOW',
    'ADMIN-PILOT-WORKFLOW', '1.0.0', '1.0.0',
    '{"workflowKey":"ADMIN_PILOT","semanticVersion":"1.0.0","startNodeId":"START","terminalNodeIds":["END"],"nodes":[{"nodeId":"START","nodeType":"START","name":"开始"},{"nodeId":"PILOT_FIELD_OPS","nodeType":"USER_TASK","name":"现场履约","stageCode":"PILOT_SURVEY","taskType":"PILOT_SURVEY","slaRef":"PILOT_RESPONSE","formRef":"admin.pilot-inbound-form"},{"nodeId":"END","nodeType":"END","name":"结束"}],"transitions":[{"transitionId":"t1","from":"START","to":"PILOT_FIELD_OPS"},{"transitionId":"t2","from":"PILOT_FIELD_OPS","to":"END"}]}',
    '73bf5f7e929cfd98fc25e0f8e332ae14cc6da8a1e4fcc3b2782dae51fe777c37',
    'PUBLISHED', now()
),
(
    '20000000-0000-4000-8000-000000000002', 'tenant-local', 'SLA',
    'PILOT_RESPONSE', '1.0.0', '1.0.0',
    '{"policyKey":"PILOT_RESPONSE","version":"1.0.0","subjectType":"TASK","taskTypes":["PILOT_SURVEY"],"startEvent":"TASK_CREATED","stopEvent":"TASK_COMPLETED","clockMode":"ELAPSED","targetDurationSeconds":14400}',
    '3ca27e3a4ce99cf98cacf1d2c8203f5aa353cc86cfa96749bd8595803cc1ceb5', 'PUBLISHED', now()
) ON CONFLICT DO NOTHING;

-- bundle_item 以 (version_id, content_digest) FK 绑定资产；替换 digest 前必须先断开再重建。
DELETE FROM cfg_configuration_bundle_item
 WHERE tenant_id = 'tenant-local'
   AND bundle_id = '30000000-0000-4000-8000-000000000001'
   AND asset_type IN ('WORKFLOW', 'FORM', 'EVIDENCE', 'SLA');

UPDATE cfg_configuration_asset_version
   SET definition = '{"workflowKey":"ADMIN_PILOT","semanticVersion":"1.0.0","startNodeId":"START","terminalNodeIds":["END"],"nodes":[{"nodeId":"START","nodeType":"START","name":"开始"},{"nodeId":"PILOT_FIELD_OPS","nodeType":"USER_TASK","name":"现场履约","stageCode":"PILOT_SURVEY","taskType":"PILOT_SURVEY","slaRef":"PILOT_RESPONSE","formRef":"admin.pilot-inbound-form"},{"nodeId":"END","nodeType":"END","name":"结束"}],"transitions":[{"transitionId":"t1","from":"START","to":"PILOT_FIELD_OPS"},{"transitionId":"t2","from":"PILOT_FIELD_OPS","to":"END"}]}',
       content_digest = '73bf5f7e929cfd98fc25e0f8e332ae14cc6da8a1e4fcc3b2782dae51fe777c37',
       published_at = now()
 WHERE version_id = '20000000-0000-4000-8000-000000000001'
   AND tenant_id = 'tenant-local';


UPDATE cfg_configuration_asset_version
   SET definition = '{"policyKey":"PILOT_RESPONSE","version":"1.0.0","subjectType":"TASK","taskTypes":["PILOT_SURVEY"],"startEvent":"TASK_CREATED","stopEvent":"TASK_COMPLETED","clockMode":"ELAPSED","targetDurationSeconds":14400}',
       content_digest = '3ca27e3a4ce99cf98cacf1d2c8203f5aa353cc86cfa96749bd8595803cc1ceb5',
       published_at = now()
 WHERE version_id = '20000000-0000-4000-8000-000000000002'
   AND tenant_id = 'tenant-local';

INSERT INTO cfg_configuration_asset_version (
    version_id, tenant_id, asset_type, asset_key, semantic_version, schema_version,
    definition, content_digest, status, published_at
) VALUES
(
    '20000000-0000-4000-8000-000000000006', 'tenant-local', 'FORM',
    'admin.pilot-inbound-form', '1.0.0', '1.0.0',
    '{"formKey":"admin.pilot-inbound-form","version":"1.0.0","stage":"PILOT_SURVEY","sections":[{"sectionKey":"survey","title":"入站现场勘察","fields":[{"fieldKey":"survey.note","label":"勘察说明","dataType":"STRING","binding":"task.input.survey.note","required":true}]}]}',
    'b868b1fe5b492009ae45f15eebb11a4cd5db42517b1cc32205b676f00a724b26',
    'PUBLISHED', now()
),
(
    '20000000-0000-4000-8000-000000000007', 'tenant-local', 'EVIDENCE',
    'admin.pilot-inbound-evidence', '1.0.0', '1.0.0',
    '{"templateKey":"admin.pilot-inbound-evidence","version":"1.0.0","title":"入站勘察资料","stage":"PILOT_SURVEY","items":[{"evidenceKey":"survey.photo","name":"勘察现场照片","mediaType":"PHOTO","required":true,"capture":{"allowCamera":true,"allowGallery":true,"minCount":1,"maxCount":1,"maxSizeBytes":1048576},"reviewPolicy":{"reviewRequired":false}}]}',
    'ece3e8cdb6a96a9c0ed709173345ed9c75dc2b37da6e4ca37fbbe70a3440322c',
    'PUBLISHED', now()
) ON CONFLICT (version_id) DO UPDATE
SET definition = EXCLUDED.definition,
    content_digest = EXCLUDED.content_digest,
    asset_key = EXCLUDED.asset_key,
    published_at = now();

INSERT INTO cfg_configuration_bundle (
    bundle_id, tenant_id, project_id, bundle_code, bundle_version, brand_code,
    service_product_code, province_code, effective_from, manifest_digest, status, published_at
) VALUES (
    '30000000-0000-4000-8000-000000000001', 'tenant-local',
    '10000000-0000-4000-8000-000000000001', 'ADMIN-PILOT-BUNDLE', '1.0.0',
    'BYD_OCEAN', 'HOME_CHARGING_SURVEY_INSTALL', '370000', now() - interval '1 day',
    repeat('c', 64), 'PUBLISHED', now()
) ON CONFLICT DO NOTHING;

INSERT INTO cfg_configuration_bundle_item (
    tenant_id, bundle_id, asset_type, asset_version_id, content_digest
) VALUES
(
    'tenant-local', '30000000-0000-4000-8000-000000000001', 'WORKFLOW',
    '20000000-0000-4000-8000-000000000001',
    '73bf5f7e929cfd98fc25e0f8e332ae14cc6da8a1e4fcc3b2782dae51fe777c37'
),
(
    'tenant-local', '30000000-0000-4000-8000-000000000001', 'SLA',
    '20000000-0000-4000-8000-000000000002',
    '3ca27e3a4ce99cf98cacf1d2c8203f5aa353cc86cfa96749bd8595803cc1ceb5'
),
(
    'tenant-local', '30000000-0000-4000-8000-000000000001', 'FORM',
    '20000000-0000-4000-8000-000000000006',
    'b868b1fe5b492009ae45f15eebb11a4cd5db42517b1cc32205b676f00a724b26'
),
(
    'tenant-local', '30000000-0000-4000-8000-000000000001', 'EVIDENCE',
    '20000000-0000-4000-8000-000000000007',
    'ece3e8cdb6a96a9c0ed709173345ed9c75dc2b37da6e4ca37fbbe70a3440322c'
) ON CONFLICT DO NOTHING;

ALTER TABLE cfg_configuration_bundle_item ENABLE TRIGGER trg_cfg_bundle_item_immutable;
ALTER TABLE cfg_configuration_asset_version ENABLE TRIGGER trg_cfg_asset_version_immutable;

INSERT INTO wo_work_order (
    id, tenant_id, project_id, client_code, brand_code, service_product_code,
    external_order_code, payload_digest, status, configuration_bundle_id,
    configuration_bundle_code, configuration_bundle_version, configuration_bundle_digest,
    province_code, city_code, district_code, customer_name, customer_mobile,
    service_address, vehicle_vin, external_dispatched_at, received_at, activated_at, version
) VALUES (
    '40000000-0000-4000-8000-000000000001', 'tenant-local',
    '10000000-0000-4000-8000-000000000001', 'BYD', 'BYD_OCEAN',
    'HOME_CHARGING_SURVEY_INSTALL', 'ADMIN-PILOT-001', repeat('d', 64), 'ACTIVE',
    '30000000-0000-4000-8000-000000000001', 'ADMIN-PILOT-BUNDLE', '1.0.0',
    repeat('c', 64), '370000', '370100', '370102', '本地试点用户', '13800000000',
    '本地试点地址', 'TESTVIN00000000001', localtimestamp - interval '2 hours',
    now() - interval '2 hours', now() - interval '110 minutes', 1
) ON CONFLICT DO NOTHING;

INSERT INTO wfl_workflow_instance (
    workflow_instance_id, tenant_id, project_id, work_order_id, configuration_bundle_id,
    workflow_definition_version_id, workflow_key, workflow_version, definition_digest,
    status, start_event_id, correlation_id, version, started_at, configuration_bundle_digest
) VALUES (
    '50000000-0000-4000-8000-000000000001', 'tenant-local',
    '10000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001',
    '30000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001',
    'ADMIN_PILOT', '1.0.0', repeat('a', 64), 'ACTIVE',
    '51000000-0000-4000-8000-000000000001', 'admin-pilot-seed', 1,
    now() - interval '110 minutes', repeat('c', 64)
) ON CONFLICT DO NOTHING;

INSERT INTO wfl_stage_instance (
    stage_instance_id, tenant_id, workflow_instance_id, work_order_id, stage_code,
    sequence_no, status, activation_event_id, version, activated_at
) VALUES (
    '60000000-0000-4000-8000-000000000001', 'tenant-local',
    '50000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001',
    'PILOT_SURVEY', 1, 'ACTIVE', '61000000-0000-4000-8000-000000000001',
    1, now() - interval '100 minutes'
) ON CONFLICT DO NOTHING;

INSERT INTO tsk_task (
    task_id, tenant_id, task_type, task_kind, business_key, payload_digest, priority,
    status, next_run_at, attempt_count, max_attempts, correlation_id, version,
    created_at, updated_at, project_id, work_order_id, workflow_instance_id,
    stage_instance_id, workflow_node_instance_id, workflow_node_id,
    workflow_definition_version_id, workflow_definition_digest, configuration_bundle_id,
    configuration_bundle_digest, stage_code, sla_ref
) VALUES (
    '70000000-0000-4000-8000-000000000001', 'tenant-local', 'PILOT_SURVEY',
    'HUMAN', 'admin-pilot:survey', repeat('e', 64), 500, 'READY',
    now() - interval '90 minutes', 0, 3, 'admin-pilot-seed', 1,
    now() - interval '90 minutes', now() - interval '90 minutes',
    '10000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001',
    '50000000-0000-4000-8000-000000000001', '60000000-0000-4000-8000-000000000001',
    '65000000-0000-4000-8000-000000000001', 'PILOT_SURVEY_NODE',
    '20000000-0000-4000-8000-000000000001', repeat('a', 64),
    '30000000-0000-4000-8000-000000000001', repeat('c', 64), 'PILOT_SURVEY',
    'PILOT_RESPONSE'
) ON CONFLICT DO NOTHING;

-- 每轮浏览器冒烟必须通过真实 assign-candidates 命令建立候选快照，不能依赖数据库预置 ACTIVE 候选。
-- 对历史版本夹具留下的 ACTIVE 候选只补齐撤销人、原因和时间，不回退 Task version，
-- 也不删除既有批次、事件或审计。
-- 若上次冒烟中断在 CLAIMED/RUNNING，后续命令仍会按状态机失败关闭，脚本不得用 SQL 伪造恢复成功。
UPDATE tsk_task_assignment
   SET status = 'REVOKED',
       effective_to = now(),
       revoked_by = 'local-fixture',
       revoke_reason_code = 'E2E_ASSIGNMENT_REPLACED'
 WHERE tenant_id = 'tenant-local'
   AND task_id = '70000000-0000-4000-8000-000000000001'
   AND assignment_kind = 'CANDIDATE'
   AND status = 'ACTIVE';

INSERT INTO sla_instance (
    sla_instance_id, tenant_id, project_id, work_order_id, task_id, sla_ref,
    policy_version_id, policy_semantic_version, policy_content_digest, clock_mode,
    target_duration_seconds, start_event_id, started_at, deadline_at, status,
    aggregate_version, correlation_id, created_at, updated_at
) VALUES (
    '80000000-0000-4000-8000-000000000001', 'tenant-local',
    '10000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001',
    '70000000-0000-4000-8000-000000000001', 'PILOT_RESPONSE',
    '20000000-0000-4000-8000-000000000002', '1.0.0',
    '3ca27e3a4ce99cf98cacf1d2c8203f5aa353cc86cfa96749bd8595803cc1ceb5', 'ELAPSED',
    14400, '81000000-0000-4000-8000-000000000001', now() - interval '90 minutes',
    now() + interval '150 minutes', 'RUNNING', 1, 'admin-pilot-seed', now(), now()
) ON CONFLICT DO NOTHING;

INSERT INTO sla_clock_segment (
    segment_id, tenant_id, sla_instance_id, segment_no, segment_type, started_at, start_event_id
) VALUES (
    '82000000-0000-4000-8000-000000000001', 'tenant-local',
    '80000000-0000-4000-8000-000000000001', 1, 'RUNNING',
    now() - interval '90 minutes', '81000000-0000-4000-8000-000000000001'
) ON CONFLICT DO NOTHING;

INSERT INTO sla_milestone (
    milestone_id, tenant_id, sla_instance_id, milestone_type, scheduled_at, status
) VALUES (
    '83000000-0000-4000-8000-000000000001', 'tenant-local',
    '80000000-0000-4000-8000-000000000001', 'TARGET_DUE',
    now() + interval '150 minutes', 'PENDING'
) ON CONFLICT DO NOTHING;

INSERT INTO rdm_work_order_timeline_entry (
    timeline_entry_id, tenant_id, project_id, work_order_id, source_event_id,
    source_module, event_type, schema_version, category, resource_type, resource_id,
    resource_version, resource_code, outcome_code, actor_id, correlation_id,
    display_template_code, display_template_version, occurred_at, received_at,
    rebuild_generation
) VALUES
(
    '90000000-0000-4000-8000-000000000001', 'tenant-local',
    '10000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001',
    '91000000-0000-4000-8000-000000000001', 'workorder', 'WorkOrderReceived', 1,
    'WORK_ORDER', 'WorkOrder', '40000000-0000-4000-8000-000000000001', 1,
    'ADMIN-PILOT-001', 'RECEIVED', 'local-fixture', 'admin-pilot-seed',
    'work-order.received', 1, now() - interval '2 hours', now() - interval '2 hours', 1
),
(
    '90000000-0000-4000-8000-000000000002', 'tenant-local',
    '10000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001',
    '91000000-0000-4000-8000-000000000002', 'task', 'HumanTaskCreated', 1,
    'TASK', 'Task', '70000000-0000-4000-8000-000000000001', 1,
    'PILOT_SURVEY', 'READY', 'local-fixture', 'admin-pilot-seed',
    'task.created', 1, now() - interval '90 minutes', now() - interval '90 minutes', 1
) ON CONFLICT DO NOTHING;

-- 本地夹具纠偏：历史种子曾把 resource_type 写成 category 风格大写，与 OpenAPI PascalCase 不一致。
UPDATE rdm_work_order_timeline_entry
   SET resource_type = 'WorkOrder'
 WHERE tenant_id = 'tenant-local'
   AND timeline_entry_id = '90000000-0000-4000-8000-000000000001'
   AND resource_type <> 'WorkOrder';
UPDATE rdm_work_order_timeline_entry
   SET resource_type = 'Task'
 WHERE tenant_id = 'tenant-local'
   AND timeline_entry_id = '90000000-0000-4000-8000-000000000002'
   AND resource_type <> 'Task';

INSERT INTO rdm_projection_checkpoint (
    projection_code, tenant_id, partition_key, rebuild_generation,
    last_source_outbox_id, last_occurred_at, processed_at, status
) VALUES (
    'work-order-core-timeline.v1', 'tenant-local', 'tenant-local', 1,
    NULL, now() - interval '90 minutes', now(), 'RUNNING'
) ON CONFLICT DO NOTHING;

-- M175：OPEN 运营异常 + HUMAN 人工接管 Task，证明 handlingTaskId → 任务详情深链。
-- 接管 Task 与生产 createHandlingTask 一致：无 project/workOrder 绑定，走 TENANT task.read。
INSERT INTO tsk_task (
    task_id, tenant_id, task_type, task_kind, business_key, payload_digest, priority,
    status, next_run_at, attempt_count, max_attempts, correlation_id, version,
    created_at, updated_at
) VALUES (
    '71000000-0000-4000-8000-000000000001', 'tenant-local',
    'operations.resolve-exception', 'HUMAN',
    'a1000000-0000-4000-8000-000000000001', repeat('f', 64), 100, 'READY',
    now() - interval '30 minutes', 0, 1, 'admin-pilot-exception-handling', 1,
    now() - interval '30 minutes', now() - interval '30 minutes'
) ON CONFLICT DO NOTHING;

INSERT INTO ops_operational_exception (
    exception_id, tenant_id, project_id, source_type, source_id, source_attempt_id,
    source_task_type, category_code, severity_code, error_code, status,
    work_order_id, task_id, handling_task_id, occurrence_count, aggregate_version,
    correlation_id, opened_at, last_detected_at
) VALUES (
    'a1000000-0000-4000-8000-000000000001', 'tenant-local',
    '10000000-0000-4000-8000-000000000001', 'TASK',
    '70000000-0000-4000-8000-000000000001',
    'a2000000-0000-4000-8000-000000000001',
    'integration.byd.submit-review', 'AUTOMATION_FINAL_FAILURE', 'P1',
    'ADMIN_PILOT_TRANSPORT_UNKNOWN', 'OPEN',
    '40000000-0000-4000-8000-000000000001',
    '70000000-0000-4000-8000-000000000001',
    '71000000-0000-4000-8000-000000000001',
    1, 1, 'admin-pilot-exception-handling',
    now() - interval '30 minutes', now() - interval '30 minutes'
) ON CONFLICT DO NOTHING;
