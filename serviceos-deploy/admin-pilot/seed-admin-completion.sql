-- M134 Admin 试点终态写链路夹具。每轮由调用脚本传入全新 UUID，不覆盖或回退历史业务事实。
-- 该夹具仅证明已接受的 M20/M21 HUMAN Task 线性 END 推进，禁止复制到生产数据库。
\set ON_ERROR_STOP on

INSERT INTO cfg_configuration_asset_version (
    version_id, tenant_id, asset_type, asset_key, semantic_version, schema_version,
    definition, content_digest, status, published_at
) VALUES (
    '20000000-0000-4000-8000-000000000003', 'tenant-local', 'WORKFLOW',
    'ADMIN-PILOT-COMPLETION-WORKFLOW', '1.0.0', '1.0.0',
    '{
      "workflowKey":"admin.pilot-completion",
      "semanticVersion":"1.0.0",
      "startNodeId":"START",
      "nodes":[
        {"nodeId":"START","nodeType":"START","name":"开始"},
        {
          "nodeId":"PILOT_COMPLETION_NODE",
          "nodeType":"USER_TASK",
          "name":"试点终态验证",
          "stageCode":"PILOT_COMPLETION",
          "taskType":"PILOT_COMPLETION",
          "formRef":"admin.pilot-completion-form"
        },
        {"nodeId":"END","nodeType":"END","name":"结束"}
      ],
      "transitions":[
        {"transitionId":"start-to-completion","from":"START","to":"PILOT_COMPLETION_NODE"},
        {"transitionId":"completion-to-end","from":"PILOT_COMPLETION_NODE","to":"END"}
      ]
    }',
    repeat('f', 64), 'PUBLISHED', now()
) ON CONFLICT DO NOTHING;

INSERT INTO cfg_configuration_asset_version (
    version_id, tenant_id, asset_type, asset_key, semantic_version, schema_version,
    definition, content_digest, status, published_at
) VALUES (
    '20000000-0000-4000-8000-000000000004', 'tenant-local', 'FORM',
    'admin.pilot-completion-form', '1.0.0', '1.0.0',
    '{
      "formKey":"admin.pilot-completion-form",
      "version":"1.0.0",
      "stage":"PILOT_COMPLETION",
      "sections":[{
        "sectionKey":"completion",
        "title":"终态验证",
        "fields":[{
          "fieldKey":"completion.note",
          "label":"完成说明",
          "dataType":"STRING",
          "binding":"task.input.completion.note",
          "required":true
        }]
      }]
    }',
    repeat('5', 64), 'PUBLISHED', now()
) ON CONFLICT DO NOTHING;

INSERT INTO cfg_configuration_bundle (
    bundle_id, tenant_id, project_id, bundle_code, bundle_version, brand_code,
    service_product_code, province_code, effective_from, manifest_digest, status, published_at
) VALUES (
    '30000000-0000-4000-8000-000000000002', 'tenant-local',
    '10000000-0000-4000-8000-000000000001', 'ADMIN-PILOT-COMPLETION-BUNDLE', '1.0.0',
    'BYD_OCEAN', 'ADMIN_PILOT_COMPLETION', '370000', now() - interval '1 day',
    repeat('9', 64), 'PUBLISHED', now()
) ON CONFLICT DO NOTHING;

INSERT INTO cfg_configuration_bundle_item (
    tenant_id, bundle_id, asset_type, asset_version_id, content_digest
) VALUES
(
    'tenant-local', '30000000-0000-4000-8000-000000000002', 'WORKFLOW',
    '20000000-0000-4000-8000-000000000003', repeat('f', 64)
),
(
    'tenant-local', '30000000-0000-4000-8000-000000000002', 'FORM',
    '20000000-0000-4000-8000-000000000004', repeat('5', 64)
) ON CONFLICT DO NOTHING;

INSERT INTO wo_work_order (
    id, tenant_id, project_id, client_code, brand_code, service_product_code,
    external_order_code, payload_digest, status, configuration_bundle_id,
    configuration_bundle_code, configuration_bundle_version, configuration_bundle_digest,
    province_code, city_code, district_code, customer_name, customer_mobile,
    service_address, vehicle_vin, external_dispatched_at, received_at, activated_at, version
) VALUES (
    :'completion_work_order_id', 'tenant-local',
    '10000000-0000-4000-8000-000000000001', 'BYD', 'BYD_OCEAN',
    'ADMIN_PILOT_COMPLETION', :'completion_external_code', repeat('8', 64), 'ACTIVE',
    '30000000-0000-4000-8000-000000000002', 'ADMIN-PILOT-COMPLETION-BUNDLE', '1.0.0',
    repeat('9', 64), '370000', '370100', '370102', '本地终态验证用户', '13800000000',
    '本地终态验证地址', 'TESTVIN00000000002', localtimestamp, now(), now(), 1
);

INSERT INTO wfl_workflow_instance (
    workflow_instance_id, tenant_id, project_id, work_order_id, configuration_bundle_id,
    workflow_definition_version_id, workflow_key, workflow_version, definition_digest,
    status, start_event_id, correlation_id, version, started_at, configuration_bundle_digest
) VALUES (
    :'completion_workflow_id', 'tenant-local',
    '10000000-0000-4000-8000-000000000001', :'completion_work_order_id',
    '30000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000003',
    'admin.pilot-completion', '1.0.0', repeat('f', 64), 'ACTIVE',
    :'completion_start_event_id', :'completion_correlation_id', 1, now(), repeat('9', 64)
);

INSERT INTO wfl_stage_instance (
    stage_instance_id, tenant_id, workflow_instance_id, work_order_id, stage_code,
    sequence_no, status, activation_event_id, version, activated_at
) VALUES (
    :'completion_stage_id', 'tenant-local', :'completion_workflow_id',
    :'completion_work_order_id', 'PILOT_COMPLETION', 1, 'ACTIVE',
    :'completion_stage_event_id', 1, now()
);

INSERT INTO tsk_task (
    task_id, tenant_id, task_type, task_kind, business_key, payload_digest, priority,
    status, next_run_at, attempt_count, max_attempts, correlation_id, version,
    created_at, updated_at, project_id, work_order_id, workflow_instance_id,
    stage_instance_id, workflow_node_instance_id, workflow_node_id,
    workflow_definition_version_id, workflow_definition_digest, configuration_bundle_id,
    configuration_bundle_digest, stage_code, form_ref
) VALUES (
    :'completion_task_id', 'tenant-local', 'PILOT_COMPLETION', 'HUMAN',
    :'completion_external_code', repeat('7', 64), 500, 'READY', now(), 0, 3,
    :'completion_correlation_id', 1, now(), now(),
    '10000000-0000-4000-8000-000000000001', :'completion_work_order_id',
    :'completion_workflow_id', :'completion_stage_id', :'completion_node_id',
    'PILOT_COMPLETION_NODE', '20000000-0000-4000-8000-000000000003', repeat('f', 64),
    '30000000-0000-4000-8000-000000000002', repeat('9', 64), 'PILOT_COMPLETION',
    'admin.pilot-completion-form'
);

INSERT INTO wfl_node_instance (
    workflow_node_instance_id, tenant_id, workflow_instance_id, stage_instance_id,
    work_order_id, node_id, task_id, status, activation_event_id, version, activated_at
) VALUES (
    :'completion_node_id', 'tenant-local', :'completion_workflow_id', :'completion_stage_id',
    :'completion_work_order_id', 'PILOT_COMPLETION_NODE', :'completion_task_id', 'ACTIVE',
    :'completion_node_event_id', 1, now()
);
