-- 本地/演示：济南网点 + Network/Technician Portal 成员与 NETWORK scope 授权。
-- 幂等；禁止用于生产。依赖本地 developer Principal 已由 grant-local-project-admin.sql 预配。
\set ON_ERROR_STOP on

-- 固定演示 UUID（d3500000-1000-…），可被 clear-demo 一并清理
-- 合作方 / 网点
INSERT INTO net_partner_organization (
    partner_organization_id, tenant_id, partner_code, partner_name,
    partner_status, aggregate_version, created_at, updated_at
) VALUES (
    'd3500000-1000-4000-8000-000000000001', 'tenant-local',
    'DEMO-HENTONG', '济南恒通新能源服务中心（合作方）',
    'ACTIVE', 1, now(), now()
) ON CONFLICT (partner_organization_id) DO NOTHING;

INSERT INTO net_service_network (
    service_network_id, tenant_id, partner_organization_id, network_code,
    network_name, network_status, aggregate_version, created_at, updated_at
) VALUES (
    'd3500000-1000-4000-8000-000000000002', 'tenant-local',
    'd3500000-1000-4000-8000-000000000001',
    'JN-HENTONG', '济南恒通新能源服务中心',
    'ACTIVE', 1, now(), now()
) ON CONFLICT (service_network_id) DO NOTHING;

-- 绑定到 admin-pilot / 演示项目（network_id 使用网点 UUID 文本）
INSERT INTO prj_project_network (
    project_network_id, tenant_id, project_id, network_id,
    valid_from, created_by, created_at
) VALUES (
    'd3500000-1000-4000-8000-00000000000d',
    'tenant-local',
    '10000000-0000-4000-8000-000000000001',
    'd3500000-1000-4000-8000-000000000002',
    now() - interval '1 day',
    'demo-fixture',
    now()
) ON CONFLICT (project_network_id) DO NOTHING;

-- 本地 developer：兼具 NETWORK_MEMBER / TECHNICIAN Persona（三门户同一账号联调）
INSERT INTO idn_principal_persona (
    persona_id, tenant_id, principal_id, persona_type, persona_status,
    valid_from, valid_to, persona_version, created_by, created_at
) VALUES
(
    'd3500000-1000-4000-8000-000000000007',
    'tenant-local', '06b612f3-a901-4b0e-bd90-86b4259cc087',
    'NETWORK_MEMBER', 'ACTIVE', now() - interval '1 day', NULL, 1,
    'demo-fixture', now()
),
(
    'd3500000-1000-4000-8000-000000000008',
    'tenant-local', '06b612f3-a901-4b0e-bd90-86b4259cc087',
    'TECHNICIAN', 'ACTIVE', now() - interval '1 day', NULL, 1,
    'demo-fixture', now()
)
ON CONFLICT (tenant_id, principal_id, persona_type) DO NOTHING;

INSERT INTO net_network_membership (
    membership_id, tenant_id, service_network_id, principal_id, membership_role,
    membership_status, valid_from, invited_by, created_at, aggregate_version
) VALUES (
    'd3500000-1000-4000-8000-000000000003',
    'tenant-local',
    'd3500000-1000-4000-8000-000000000002',
    '06b612f3-a901-4b0e-bd90-86b4259cc087',
    'MANAGER',
    'ACTIVE',
    now() - interval '1 day',
    'demo-fixture',
    now(),
    1
) ON CONFLICT (membership_id) DO NOTHING;

-- 张师傅：绑定同一 developer，便于本地师傅端登录；李师傅仅作指派目标
INSERT INTO net_technician_profile (
    technician_profile_id, tenant_id, principal_id, display_name, profile_status,
    aggregate_version, created_at, updated_at
) VALUES (
    'd3500000-1000-4000-8000-000000000004',
    'tenant-local',
    '06b612f3-a901-4b0e-bd90-86b4259cc087',
    '张师傅',
    'ACTIVE',
    1, now(), now()
) ON CONFLICT (technician_profile_id) DO NOTHING;

INSERT INTO idn_security_principal (
    principal_id, tenant_id, principal_type, principal_status,
    aggregate_version, created_at, updated_at
) VALUES (
    'd3500000-1000-4000-8000-000000000010',
    'tenant-local', 'USER', 'ACTIVE', 1, now(), now()
) ON CONFLICT (principal_id) DO NOTHING;

INSERT INTO idn_person_profile (
    principal_id, tenant_id, display_name, employee_number,
    profile_version, created_at, updated_at, updated_by
) VALUES (
    'd3500000-1000-4000-8000-000000000010',
    'tenant-local', '李师傅', 'DEMO-TECH-LI',
    1, now(), now(), 'demo-fixture'
) ON CONFLICT (principal_id) DO NOTHING;

INSERT INTO net_technician_profile (
    technician_profile_id, tenant_id, principal_id, display_name, profile_status,
    aggregate_version, created_at, updated_at
) VALUES (
    'd3500000-1000-4000-8000-000000000011',
    'tenant-local',
    'd3500000-1000-4000-8000-000000000010',
    '李师傅',
    'ACTIVE',
    1, now(), now()
) ON CONFLICT (technician_profile_id) DO NOTHING;

INSERT INTO net_network_technician_membership (
    membership_id, tenant_id, service_network_id, technician_profile_id,
    membership_status, valid_from, created_by, created_at, aggregate_version
) VALUES
(
    'd3500000-1000-4000-8000-000000000005',
    'tenant-local',
    'd3500000-1000-4000-8000-000000000002',
    'd3500000-1000-4000-8000-000000000004',
    'ACTIVE', now() - interval '1 day', 'demo-fixture', now(), 1
),
(
    'd3500000-1000-4000-8000-000000000012',
    'tenant-local',
    'd3500000-1000-4000-8000-000000000002',
    'd3500000-1000-4000-8000-000000000011',
    'ACTIVE', now() - interval '1 day', 'demo-fixture', now(), 1
)
ON CONFLICT (membership_id) DO NOTHING;

INSERT INTO net_technician_qualification (
    qualification_id, tenant_id, technician_profile_id, qualification_code,
    qualification_status, valid_from, valid_to, submitted_by, submitted_at,
    decided_by, decided_at, decision_reason, aggregate_version
) VALUES
(
    'd3500000-1000-4000-8000-000000000006',
    'tenant-local',
    'd3500000-1000-4000-8000-000000000004',
    'EV-INSTALL', 'APPROVED',
    now() - interval '1 day', now() + interval '365 day',
    'demo-fixture', now(), 'demo-fixture', now(), '演示资质', 1
),
(
    'd3500000-1000-4000-8000-000000000013',
    'tenant-local',
    'd3500000-1000-4000-8000-000000000011',
    'EV-INSTALL', 'APPROVED',
    now() - interval '1 day', now() + interval '365 day',
    'demo-fixture', now(), 'demo-fixture', now(), '演示资质', 1
)
ON CONFLICT (qualification_id) DO NOTHING;

-- Network Portal 角色（NETWORK scope 授予到济南网点）
INSERT INTO auth_role (
    role_id, tenant_id, role_code, role_name, role_status, created_at
) VALUES (
    'd3500000-1000-4000-8000-000000000009',
    'tenant-local', 'demo-network-dispatcher', '演示网点调度', 'ACTIVE', now()
) ON CONFLICT (tenant_id, role_code) DO NOTHING;

INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
VALUES
    ('d3500000-1000-4000-8000-000000000009', 'networkPortal.acceptAssignment', now()),
    ('d3500000-1000-4000-8000-000000000009', 'networkPortal.assignTechnician', now()),
    ('d3500000-1000-4000-8000-000000000009', 'networkPortal.reassignTechnician', now()),
    ('d3500000-1000-4000-8000-000000000009', 'networkPortal.manageAppointment', now()),
    ('d3500000-1000-4000-8000-000000000009', 'networkPortal.manageTechnician', now()),
    ('d3500000-1000-4000-8000-000000000009', 'dispatch.assignment.manage', now()),
    ('d3500000-1000-4000-8000-000000000009', 'dispatch.capacity.configure', now()),
    ('d3500000-1000-4000-8000-000000000009', 'networkTask.read', now()),
    ('d3500000-1000-4000-8000-000000000009', 'technician.readOwnNetwork', now()),
    ('d3500000-1000-4000-8000-000000000009', 'evidence.read', now()),
    ('d3500000-1000-4000-8000-000000000009', 'evidence.submitOnBehalf', now()),
    ('d3500000-1000-4000-8000-000000000009', 'operations.exception.read', now()),
    ('d3500000-1000-4000-8000-000000000009', 'appointment.read', now()),
    ('d3500000-1000-4000-8000-000000000009', 'appointment.propose', now()),
    ('d3500000-1000-4000-8000-000000000009', 'appointment.manage', now()),
    ('d3500000-1000-4000-8000-000000000009', 'appointment.cancel', now()),
    ('d3500000-1000-4000-8000-000000000009', 'appointment.recordContact', now()),
    ('d3500000-1000-4000-8000-000000000009', 'sla.read', now()),
    ('d3500000-1000-4000-8000-000000000009', 'visit.read', now()),
    ('d3500000-1000-4000-8000-000000000009', 'form.read', now())
ON CONFLICT (role_id, capability_code) DO NOTHING;

INSERT INTO auth_role_grant (
    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
    valid_from, source_code, approval_ref, created_at
) VALUES (
    'd3500000-1000-4000-8000-00000000000b',
    'tenant-local',
    '06b612f3-a901-4b0e-bd90-86b4259cc087',
    'd3500000-1000-4000-8000-000000000009',
    'NETWORK',
    'd3500000-1000-4000-8000-000000000002',
    now() - interval '1 day',
    'DEMO_FIXTURE',
    'local-demo-only',
    now()
) ON CONFLICT (grant_id) DO NOTHING;

-- Technician Portal 读取指派任务
INSERT INTO auth_role (
    role_id, tenant_id, role_code, role_name, role_status, created_at
) VALUES (
    'd3500000-1000-4000-8000-00000000000a',
    'tenant-local', 'demo-technician', '演示服务师傅', 'ACTIVE', now()
) ON CONFLICT (tenant_id, role_code) DO NOTHING;

INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
VALUES
    ('d3500000-1000-4000-8000-00000000000a', 'task.readAssigned', now()),
    ('d3500000-1000-4000-8000-00000000000a', 'appointment.read', now()),
    ('d3500000-1000-4000-8000-00000000000a', 'appointment.propose', now()),
    ('d3500000-1000-4000-8000-00000000000a', 'appointment.manage', now()),
    ('d3500000-1000-4000-8000-00000000000a', 'visit.read', now()),
    ('d3500000-1000-4000-8000-00000000000a', 'visit.checkIn', now()),
    ('d3500000-1000-4000-8000-00000000000a', 'visit.checkOut', now()),
    ('d3500000-1000-4000-8000-00000000000a', 'form.read', now()),
    ('d3500000-1000-4000-8000-00000000000a', 'form.submit', now()),
    ('d3500000-1000-4000-8000-00000000000a', 'evidence.read', now()),
    ('d3500000-1000-4000-8000-00000000000a', 'evidence.submit', now()),
    ('d3500000-1000-4000-8000-00000000000a', 'file.upload', now()),
    ('d3500000-1000-4000-8000-00000000000a', 'file.download', now())
ON CONFLICT (role_id, capability_code) DO NOTHING;

INSERT INTO auth_role_grant (
    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
    valid_from, source_code, approval_ref, created_at
) VALUES (
    'd3500000-1000-4000-8000-00000000000c',
    'tenant-local',
    '06b612f3-a901-4b0e-bd90-86b4259cc087',
    'd3500000-1000-4000-8000-00000000000a',
    'NETWORK',
    'd3500000-1000-4000-8000-000000000002',
    now() - interval '1 day',
    'DEMO_FIXTURE',
    'local-demo-only',
    now()
) ON CONFLICT (grant_id) DO NOTHING;
