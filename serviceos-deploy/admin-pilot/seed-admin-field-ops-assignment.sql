-- M136 Admin 试点现场履约写链路：为每轮新建 Task 注入 ACTIVE ServiceAssignment。
-- Visit check-in 要求网点/师傅责任与 Task RESPONSIBLE 对齐；夹具不经由 Admin 派单 HTTP
-- （该表面尚未建立），只在本地开发库写入与开发者 principal 对齐的最小责任事实。
\set ON_ERROR_STOP on

INSERT INTO dsp_service_assignment (
    service_assignment_id, tenant_id, work_order_id, task_id,
    responsibility_level, assignee_id, business_type, source_decision_id,
    status, activation_saga_id, effective_from, authority_assignment_id,
    authority_version, fence_decision_id, fence_policy_version,
    created_by, created_at
) VALUES
(
    :'field_ops_network_assignment_id', 'tenant-local',
    :'field_ops_work_order_id', :'field_ops_task_id',
    'NETWORK', 'admin-pilot-network-1', 'INSTALLATION',
    'ADMIN-PILOT-FIELD-OPS-NETWORK', 'ACTIVE', :'field_ops_network_saga_id',
    now(), 'ADMIN-PILOT-FIELD-OPS-AUTHORITY', 1,
    'ADMIN-PILOT-FIELD-OPS-FENCE', 'geo-pilot-v1',
    'admin-pilot-fixture', now()
),
(
    :'field_ops_technician_assignment_id', 'tenant-local',
    :'field_ops_work_order_id', :'field_ops_task_id',
    'TECHNICIAN', '06b612f3-a901-4b0e-bd90-86b4259cc087', 'INSTALLATION',
    'ADMIN-PILOT-FIELD-OPS-TECHNICIAN', 'ACTIVE', :'field_ops_technician_saga_id',
    now(), 'ADMIN-PILOT-FIELD-OPS-AUTHORITY', 1,
    'ADMIN-PILOT-FIELD-OPS-FENCE', 'geo-pilot-v1',
    'admin-pilot-fixture', now()
);
