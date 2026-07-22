-- 仅用于 product-data:reset。Keycloak 固定 subject 与内部 Principal 一一绑定，
-- 各角色使用最小能力集合，便于真实浏览器验证允许与拒绝路径。
WITH product_users(principal_id, display_name, employee_number) AS (
    VALUES
        ('88aa1111-2222-4333-8444-555566667777'::uuid, '只读观察员', 'LOCAL-VIEWER'),
        ('11111111-1111-4111-8111-111111111111'::uuid, '运营专员', 'LOCAL-OPERATOR'),
        ('22222222-2222-4222-8222-222222222222'::uuid, '平台调度', 'LOCAL-DISPATCHER'),
        ('33333333-3333-4333-8333-333333333333'::uuid, '质量审核', 'LOCAL-REVIEWER'),
        ('44444444-4444-4444-8444-444444444444'::uuid, '项目经理', 'LOCAL-PROJECT-MANAGER'),
        ('55555555-5555-4555-8555-555555555555'::uuid, '项目助理', 'LOCAL-PROJECT-ASSISTANT'),
        ('66666666-6666-4666-8666-666666666666'::uuid, '网点负责人', 'LOCAL-NETWORK-MANAGER'),
        ('77777777-7777-4777-8777-777777777777'::uuid, '网点调度', 'LOCAL-NETWORK-DISPATCHER')
)
INSERT INTO idn_security_principal (
    principal_id, tenant_id, principal_type, principal_status,
    aggregate_version, created_at, updated_at
)
SELECT principal_id, 'tenant-local', 'USER', 'ACTIVE', 1, now(), now()
FROM product_users
ON CONFLICT (principal_id) DO NOTHING;

WITH product_users(principal_id, display_name, employee_number) AS (
    VALUES
        ('88aa1111-2222-4333-8444-555566667777'::uuid, '只读观察员', 'LOCAL-VIEWER'),
        ('11111111-1111-4111-8111-111111111111'::uuid, '运营专员', 'LOCAL-OPERATOR'),
        ('22222222-2222-4222-8222-222222222222'::uuid, '平台调度', 'LOCAL-DISPATCHER'),
        ('33333333-3333-4333-8333-333333333333'::uuid, '质量审核', 'LOCAL-REVIEWER'),
        ('44444444-4444-4444-8444-444444444444'::uuid, '项目经理', 'LOCAL-PROJECT-MANAGER'),
        ('55555555-5555-4555-8555-555555555555'::uuid, '项目助理', 'LOCAL-PROJECT-ASSISTANT'),
        ('66666666-6666-4666-8666-666666666666'::uuid, '网点负责人', 'LOCAL-NETWORK-MANAGER'),
        ('77777777-7777-4777-8777-777777777777'::uuid, '网点调度', 'LOCAL-NETWORK-DISPATCHER')
)
INSERT INTO idn_person_profile (
    principal_id, tenant_id, display_name, employee_number,
    profile_version, created_at, updated_at, updated_by
)
SELECT principal_id, 'tenant-local', display_name, employee_number, 1, now(), now(), 'product-data-reset'
FROM product_users
ON CONFLICT (principal_id) DO UPDATE
SET display_name = EXCLUDED.display_name,
    employee_number = EXCLUDED.employee_number,
    updated_at = now(),
    updated_by = 'product-data-reset';

WITH product_users(principal_id) AS (
    VALUES
        ('88aa1111-2222-4333-8444-555566667777'::uuid),
        ('11111111-1111-4111-8111-111111111111'::uuid),
        ('22222222-2222-4222-8222-222222222222'::uuid),
        ('33333333-3333-4333-8333-333333333333'::uuid),
        ('44444444-4444-4444-8444-444444444444'::uuid),
        ('55555555-5555-4555-8555-555555555555'::uuid),
        ('66666666-6666-4666-8666-666666666666'::uuid),
        ('77777777-7777-4777-8777-777777777777'::uuid)
)
INSERT INTO idn_principal_persona (
    persona_id, tenant_id, principal_id, persona_type, persona_status,
    valid_from, persona_version, created_by, created_at
)
SELECT gen_random_uuid(), 'tenant-local', principal_id, 'INTERNAL_EMPLOYEE', 'ACTIVE',
       now() - interval '1 day', 1, 'product-data-reset', now()
FROM product_users
ON CONFLICT (tenant_id, principal_id, persona_type) DO NOTHING;

WITH product_users(principal_id) AS (
    VALUES
        ('88aa1111-2222-4333-8444-555566667777'::uuid),
        ('11111111-1111-4111-8111-111111111111'::uuid),
        ('22222222-2222-4222-8222-222222222222'::uuid),
        ('33333333-3333-4333-8333-333333333333'::uuid),
        ('44444444-4444-4444-8444-444444444444'::uuid),
        ('55555555-5555-4555-8555-555555555555'::uuid),
        ('66666666-6666-4666-8666-666666666666'::uuid),
        ('77777777-7777-4777-8777-777777777777'::uuid)
)
INSERT INTO idn_identity_link (
    identity_link_id, tenant_id, principal_id, issuer, subject_value,
    client_id, linked_by, linked_at
)
SELECT gen_random_uuid(), 'tenant-local', principal_id,
       'http://localhost:8081/realms/serviceos', principal_id::text,
       'serviceos-local-cli', 'product-data-reset', now()
FROM product_users
ON CONFLICT (tenant_id, issuer, subject_value) DO NOTHING;

INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
VALUES
    ('90000000-0000-4000-8000-000000000001', 'tenant-local', 'product-viewer', '产品只读观察员', 'ACTIVE', now()),
    ('90000000-0000-4000-8000-000000000002', 'tenant-local', 'product-operator', '产品运营专员', 'ACTIVE', now()),
    ('90000000-0000-4000-8000-000000000003', 'tenant-local', 'product-dispatcher', '平台调度员', 'ACTIVE', now()),
    ('90000000-0000-4000-8000-000000000004', 'tenant-local', 'product-reviewer', '质量审核员', 'ACTIVE', now()),
    ('90000000-0000-4000-8000-000000000005', 'tenant-local', 'product-project-team', '项目协同人员', 'ACTIVE', now()),
    ('90000000-0000-4000-8000-000000000006', 'tenant-local', 'product-network-manager', '网点负责人', 'ACTIVE', now()),
    ('90000000-0000-4000-8000-000000000007', 'tenant-local', 'product-network-dispatcher', '网点调度员', 'ACTIVE', now())
ON CONFLICT (tenant_id, role_code) DO NOTHING;

INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
VALUES
    ('90000000-0000-4000-8000-000000000001', 'workOrder.read', now()),
    ('90000000-0000-4000-8000-000000000002', 'project.read', now()),
    ('90000000-0000-4000-8000-000000000002', 'workOrder.read', now()),
    ('90000000-0000-4000-8000-000000000002', 'task.read', now()),
    ('90000000-0000-4000-8000-000000000002', 'sla.read', now()),
    ('90000000-0000-4000-8000-000000000002', 'dispatch.read', now()),
    ('90000000-0000-4000-8000-000000000003', 'project.read', now()),
    ('90000000-0000-4000-8000-000000000003', 'workOrder.read', now()),
    ('90000000-0000-4000-8000-000000000003', 'task.read', now()),
    ('90000000-0000-4000-8000-000000000003', 'dispatch.read', now()),
    ('90000000-0000-4000-8000-000000000003', 'dispatch.assignment.manage', now()),
    ('90000000-0000-4000-8000-000000000004', 'workOrder.read', now()),
    ('90000000-0000-4000-8000-000000000004', 'task.read', now()),
    ('90000000-0000-4000-8000-000000000004', 'evidence.read', now()),
    ('90000000-0000-4000-8000-000000000004', 'evidence.review', now()),
    ('90000000-0000-4000-8000-000000000004', 'review.reopen', now()),
    ('90000000-0000-4000-8000-000000000005', 'project.read', now()),
    ('90000000-0000-4000-8000-000000000005', 'workOrder.read', now()),
    ('90000000-0000-4000-8000-000000000005', 'task.read', now()),
    ('90000000-0000-4000-8000-000000000005', 'sla.read', now()),
    ('90000000-0000-4000-8000-000000000006', 'network.read', now()),
    ('90000000-0000-4000-8000-000000000006', 'workOrder.read', now()),
    ('90000000-0000-4000-8000-000000000006', 'dispatch.read', now()),
    ('90000000-0000-4000-8000-000000000007', 'network.read', now()),
    ('90000000-0000-4000-8000-000000000007', 'workOrder.read', now()),
    ('90000000-0000-4000-8000-000000000007', 'dispatch.read', now()),
    ('90000000-0000-4000-8000-000000000007', 'dispatch.assignment.manage', now())
ON CONFLICT (role_id, capability_code) DO NOTHING;

INSERT INTO auth_role_grant (
    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
    valid_from, source_code, approval_ref, created_at
)
VALUES
    ('91000000-0000-4000-8000-000000000001', 'tenant-local', '88aa1111-2222-4333-8444-555566667777', '90000000-0000-4000-8000-000000000001', 'TENANT', 'tenant-local', now(), 'LOCAL_FIXTURE', 'local-only', now()),
    ('91000000-0000-4000-8000-000000000002', 'tenant-local', '11111111-1111-4111-8111-111111111111', '90000000-0000-4000-8000-000000000002', 'TENANT', 'tenant-local', now(), 'LOCAL_FIXTURE', 'local-only', now()),
    ('91000000-0000-4000-8000-000000000003', 'tenant-local', '22222222-2222-4222-8222-222222222222', '90000000-0000-4000-8000-000000000003', 'TENANT', 'tenant-local', now(), 'LOCAL_FIXTURE', 'local-only', now()),
    ('91000000-0000-4000-8000-000000000004', 'tenant-local', '33333333-3333-4333-8333-333333333333', '90000000-0000-4000-8000-000000000004', 'TENANT', 'tenant-local', now(), 'LOCAL_FIXTURE', 'local-only', now()),
    ('91000000-0000-4000-8000-000000000005', 'tenant-local', '44444444-4444-4444-8444-444444444444', '90000000-0000-4000-8000-000000000005', 'TENANT', 'tenant-local', now(), 'LOCAL_FIXTURE', 'local-only', now()),
    ('91000000-0000-4000-8000-000000000006', 'tenant-local', '55555555-5555-4555-8555-555555555555', '90000000-0000-4000-8000-000000000005', 'TENANT', 'tenant-local', now(), 'LOCAL_FIXTURE', 'local-only', now()),
    ('91000000-0000-4000-8000-000000000007', 'tenant-local', '66666666-6666-4666-8666-666666666666', '90000000-0000-4000-8000-000000000006', 'TENANT', 'tenant-local', now(), 'LOCAL_FIXTURE', 'local-only', now()),
    ('91000000-0000-4000-8000-000000000008', 'tenant-local', '77777777-7777-4777-8777-777777777777', '90000000-0000-4000-8000-000000000007', 'TENANT', 'tenant-local', now(), 'LOCAL_FIXTURE', 'local-only', now())
ON CONFLICT (grant_id) DO NOTHING;

UPDATE auth_tenant_grant_generation
SET generation = generation + 1, updated_at = now()
WHERE tenant_id = 'tenant-local';
