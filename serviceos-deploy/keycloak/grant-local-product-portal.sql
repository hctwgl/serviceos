-- 仅用于 product-data:reset，必须在 seed.mjs 完成之后执行（依赖种子创建的网点与师傅主体）。
-- 补齐比亚迪产品场景的三端登录身份：
--   1. platform-admin 平台超级管理员：复用 local-project-admin 角色（TENANT scope）；
--   2. network-manager / network-dispatcher：绑定济南历下服务中心 NETWORK_MEMBER 身份、
--      网点成员关系与网点 Portal 角色（NETWORK scope），使网点端可登录、接单、派师傅、预约；
--   3. 李建国 / 赵海峰 / 周志强三位师傅：绑定 Keycloak 登录身份并授予师傅 Portal 角色
--     （NETWORK scope），使师傅端可登录、签到、提交表单与资料、完成任务。
-- 幂等；禁止用于生产。
\set ON_ERROR_STOP on

-- 1. 平台超级管理员 -----------------------------------------------------------

INSERT INTO idn_security_principal (
    principal_id, tenant_id, principal_type, principal_status,
    aggregate_version, created_at, updated_at
) VALUES (
    'aa100000-0000-4000-8000-000000000001', 'tenant-local', 'USER', 'ACTIVE', 1, now(), now()
) ON CONFLICT (principal_id) DO NOTHING;

INSERT INTO idn_person_profile (
    principal_id, tenant_id, display_name, employee_number,
    profile_version, created_at, updated_at, updated_by
) VALUES (
    'aa100000-0000-4000-8000-000000000001', 'tenant-local', '平台超级管理员',
    'LOCAL-PLATFORM-ADMIN', 1, now(), now(), 'product-data-reset'
) ON CONFLICT (principal_id) DO NOTHING;

INSERT INTO idn_principal_persona (
    persona_id, tenant_id, principal_id, persona_type, persona_status,
    valid_from, valid_to, persona_version, created_by, created_at
) VALUES (
    'aa100000-0000-4000-8000-000000000101',
    'tenant-local', 'aa100000-0000-4000-8000-000000000001',
    'INTERNAL_EMPLOYEE', 'ACTIVE', now() - interval '1 day', NULL, 1,
    'product-data-reset', now()
) ON CONFLICT (tenant_id, principal_id, persona_type) DO NOTHING;

INSERT INTO idn_identity_link (
    identity_link_id, tenant_id, principal_id, issuer, subject_value,
    client_id, linked_by, linked_at
) VALUES (
    'aa100000-0000-4000-8000-000000000102', 'tenant-local',
    'aa100000-0000-4000-8000-000000000001',
    'http://localhost:8081/realms/serviceos', 'aa100000-0000-4000-8000-000000000001',
    'serviceos-local-cli', 'product-data-reset', now()
) ON CONFLICT (tenant_id, issuer, subject_value) DO NOTHING;

-- 与 developer 相同的本地项目管理员角色（TENANT scope，含身份/授权/派单/审核全集）
INSERT INTO auth_role_grant (
    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
    valid_from, source_code, approval_ref, created_at
) VALUES (
    'aa100000-0000-4000-8000-000000000103',
    'tenant-local', 'aa100000-0000-4000-8000-000000000001',
    'bf64aa35-11cb-40bc-b301-10b5853049b3',
    'TENANT', 'tenant-local', now(), 'LOCAL_FIXTURE', 'local-only', now()
) ON CONFLICT (grant_id) DO NOTHING;

-- 2. 网点端身份 ---------------------------------------------------------------

-- 网点负责人 / 网点调度追加 NETWORK_MEMBER Persona
INSERT INTO idn_principal_persona (
    persona_id, tenant_id, principal_id, persona_type, persona_status,
    valid_from, valid_to, persona_version, created_by, created_at
) VALUES
(
    'aa100000-0000-4000-8000-000000000211',
    'tenant-local', '66666666-6666-4666-8666-666666666666',
    'NETWORK_MEMBER', 'ACTIVE', now() - interval '1 day', NULL, 1,
    'product-data-reset', now()
),
(
    'aa100000-0000-4000-8000-000000000212',
    'tenant-local', '77777777-7777-4777-8777-777777777777',
    'NETWORK_MEMBER', 'ACTIVE', now() - interval '1 day', NULL, 1,
    'product-data-reset', now()
)
ON CONFLICT (tenant_id, principal_id, persona_type) DO NOTHING;

-- 网点成员关系：负责人 MANAGER、调度 STAFF，挂到种子网点济南历下服务中心
WITH net AS (
    SELECT service_network_id FROM net_service_network
    WHERE tenant_id = 'tenant-local' AND network_code = 'JINAN-LIXIA'
)
INSERT INTO net_network_membership (
    membership_id, tenant_id, service_network_id, principal_id, membership_role,
    membership_status, valid_from, invited_by, created_at, aggregate_version
)
SELECT 'aa100000-0000-4000-8000-000000000201', 'tenant-local', net.service_network_id,
       '66666666-6666-4666-8666-666666666666', 'MANAGER', 'ACTIVE',
       now() - interval '1 day', 'product-data-reset', now(), 1
FROM net
ON CONFLICT (membership_id) DO NOTHING;

WITH net AS (
    SELECT service_network_id FROM net_service_network
    WHERE tenant_id = 'tenant-local' AND network_code = 'JINAN-LIXIA'
)
INSERT INTO net_network_membership (
    membership_id, tenant_id, service_network_id, principal_id, membership_role,
    membership_status, valid_from, invited_by, created_at, aggregate_version
)
SELECT 'aa100000-0000-4000-8000-000000000202', 'tenant-local', net.service_network_id,
       '77777777-7777-4777-8777-777777777777', 'STAFF', 'ACTIVE',
       now() - interval '1 day', 'product-data-reset', now(), 1
FROM net
ON CONFLICT (membership_id) DO NOTHING;

-- 网点 Portal 角色：接单、派师傅、预约、代补资料与门户读取能力
INSERT INTO auth_role (
    role_id, tenant_id, role_code, role_name, role_status, created_at
) VALUES (
    'aa100000-0000-4000-8000-000000000301',
    'tenant-local', 'product-network-portal', '网点运营（产品场景）', 'ACTIVE', now()
) ON CONFLICT (tenant_id, role_code) DO NOTHING;

INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
VALUES
    ('aa100000-0000-4000-8000-000000000301', 'networkPortal.acceptAssignment', now()),
    ('aa100000-0000-4000-8000-000000000301', 'networkPortal.assignTechnician', now()),
    ('aa100000-0000-4000-8000-000000000301', 'networkPortal.reassignTechnician', now()),
    ('aa100000-0000-4000-8000-000000000301', 'networkPortal.manageAppointment', now()),
    ('aa100000-0000-4000-8000-000000000301', 'networkPortal.manageTechnician', now()),
    ('aa100000-0000-4000-8000-000000000301', 'dispatch.assignment.manage', now()),
    ('aa100000-0000-4000-8000-000000000301', 'dispatch.capacity.configure', now()),
    ('aa100000-0000-4000-8000-000000000301', 'networkTask.read', now()),
    ('aa100000-0000-4000-8000-000000000301', 'technician.readOwnNetwork', now()),
    ('aa100000-0000-4000-8000-000000000301', 'evidence.read', now()),
    ('aa100000-0000-4000-8000-000000000301', 'evidence.submitOnBehalf', now()),
    ('aa100000-0000-4000-8000-000000000301', 'operations.exception.read', now()),
    ('aa100000-0000-4000-8000-000000000301', 'appointment.read', now()),
    ('aa100000-0000-4000-8000-000000000301', 'appointment.propose', now()),
    ('aa100000-0000-4000-8000-000000000301', 'appointment.manage', now()),
    ('aa100000-0000-4000-8000-000000000301', 'appointment.cancel', now()),
    ('aa100000-0000-4000-8000-000000000301', 'appointment.recordContact', now()),
    ('aa100000-0000-4000-8000-000000000301', 'sla.read', now()),
    ('aa100000-0000-4000-8000-000000000301', 'visit.read', now()),
    ('aa100000-0000-4000-8000-000000000301', 'form.read', now()),
    ('aa100000-0000-4000-8000-000000000301', 'file.download', now())
ON CONFLICT (role_id, capability_code) DO NOTHING;

WITH net AS (
    SELECT service_network_id FROM net_service_network
    WHERE tenant_id = 'tenant-local' AND network_code = 'JINAN-LIXIA'
)
INSERT INTO auth_role_grant (
    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
    valid_from, source_code, approval_ref, created_at
)
SELECT 'aa100000-0000-4000-8000-000000000311', 'tenant-local',
       '66666666-6666-4666-8666-666666666666',
       'aa100000-0000-4000-8000-000000000301',
       'NETWORK', net.service_network_id::text,
       now() - interval '1 day', 'LOCAL_FIXTURE', 'local-only', now()
FROM net
ON CONFLICT (grant_id) DO NOTHING;

WITH net AS (
    SELECT service_network_id FROM net_service_network
    WHERE tenant_id = 'tenant-local' AND network_code = 'JINAN-LIXIA'
)
INSERT INTO auth_role_grant (
    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
    valid_from, source_code, approval_ref, created_at
)
SELECT 'aa100000-0000-4000-8000-000000000312', 'tenant-local',
       '77777777-7777-4777-8777-777777777777',
       'aa100000-0000-4000-8000-000000000301',
       'NETWORK', net.service_network_id::text,
       now() - interval '1 day', 'LOCAL_FIXTURE', 'local-only', now()
FROM net
ON CONFLICT (grant_id) DO NOTHING;

-- 3. 师傅端身份 ---------------------------------------------------------------

-- 种子师傅主体（seed.mjs 按工号创建）绑定 Keycloak 登录身份
WITH tech AS (
    SELECT principal_id FROM idn_person_profile
    WHERE tenant_id = 'tenant-local' AND employee_number = 'SO-TECH-031'
)
INSERT INTO idn_identity_link (
    identity_link_id, tenant_id, principal_id, issuer, subject_value,
    client_id, linked_by, linked_at
)
SELECT 'aa100000-0000-4000-8000-000000000431', 'tenant-local', tech.principal_id,
       'http://localhost:8081/realms/serviceos', 'aa100000-0000-4000-8000-000000000031',
       'serviceos-local-cli', 'product-data-reset', now()
FROM tech
ON CONFLICT (tenant_id, issuer, subject_value) DO NOTHING;

WITH tech AS (
    SELECT principal_id FROM idn_person_profile
    WHERE tenant_id = 'tenant-local' AND employee_number = 'SO-TECH-027'
)
INSERT INTO idn_identity_link (
    identity_link_id, tenant_id, principal_id, issuer, subject_value,
    client_id, linked_by, linked_at
)
SELECT 'aa100000-0000-4000-8000-000000000427', 'tenant-local', tech.principal_id,
       'http://localhost:8081/realms/serviceos', 'aa100000-0000-4000-8000-000000000027',
       'serviceos-local-cli', 'product-data-reset', now()
FROM tech
ON CONFLICT (tenant_id, issuer, subject_value) DO NOTHING;

WITH tech AS (
    SELECT principal_id FROM idn_person_profile
    WHERE tenant_id = 'tenant-local' AND employee_number = 'SO-TECH-044'
)
INSERT INTO idn_identity_link (
    identity_link_id, tenant_id, principal_id, issuer, subject_value,
    client_id, linked_by, linked_at
)
SELECT 'aa100000-0000-4000-8000-000000000444', 'tenant-local', tech.principal_id,
       'http://localhost:8081/realms/serviceos', 'aa100000-0000-4000-8000-000000000044',
       'serviceos-local-cli', 'product-data-reset', now()
FROM tech
ON CONFLICT (tenant_id, issuer, subject_value) DO NOTHING;

-- 师傅 Portal 角色：任务 Feed、签到签退、表单、资料、完工
INSERT INTO auth_role (
    role_id, tenant_id, role_code, role_name, role_status, created_at
) VALUES (
    'aa100000-0000-4000-8000-000000000501',
    'tenant-local', 'product-technician', '服务师傅（产品场景）', 'ACTIVE', now()
) ON CONFLICT (tenant_id, role_code) DO NOTHING;

INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
VALUES
    ('aa100000-0000-4000-8000-000000000501', 'task.readAssigned', now()),
    ('aa100000-0000-4000-8000-000000000501', 'appointment.read', now()),
    ('aa100000-0000-4000-8000-000000000501', 'appointment.propose', now()),
    ('aa100000-0000-4000-8000-000000000501', 'appointment.manage', now()),
    ('aa100000-0000-4000-8000-000000000501', 'visit.read', now()),
    ('aa100000-0000-4000-8000-000000000501', 'visit.checkIn', now()),
    ('aa100000-0000-4000-8000-000000000501', 'visit.checkOut', now()),
    ('aa100000-0000-4000-8000-000000000501', 'visit.interrupt', now()),
    ('aa100000-0000-4000-8000-000000000501', 'task.complete', now()),
    ('aa100000-0000-4000-8000-000000000501', 'form.read', now()),
    ('aa100000-0000-4000-8000-000000000501', 'form.submit', now()),
    ('aa100000-0000-4000-8000-000000000501', 'evidence.read', now()),
    ('aa100000-0000-4000-8000-000000000501', 'evidence.submit', now()),
    ('aa100000-0000-4000-8000-000000000501', 'file.upload', now()),
    ('aa100000-0000-4000-8000-000000000501', 'file.download', now())
ON CONFLICT (role_id, capability_code) DO NOTHING;

WITH net AS (
    SELECT service_network_id FROM net_service_network
    WHERE tenant_id = 'tenant-local' AND network_code = 'JINAN-LIXIA'
), tech AS (
    SELECT principal_id FROM idn_person_profile
    WHERE tenant_id = 'tenant-local' AND employee_number = 'SO-TECH-031'
)
INSERT INTO auth_role_grant (
    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
    valid_from, source_code, approval_ref, created_at
)
SELECT 'aa100000-0000-4000-8000-000000000531', 'tenant-local', tech.principal_id::text,
       'aa100000-0000-4000-8000-000000000501',
       'NETWORK', net.service_network_id::text,
       now() - interval '1 day', 'LOCAL_FIXTURE', 'local-only', now()
FROM net, tech
ON CONFLICT (grant_id) DO NOTHING;

WITH net AS (
    SELECT service_network_id FROM net_service_network
    WHERE tenant_id = 'tenant-local' AND network_code = 'JINAN-LIXIA'
), tech AS (
    SELECT principal_id FROM idn_person_profile
    WHERE tenant_id = 'tenant-local' AND employee_number = 'SO-TECH-027'
)
INSERT INTO auth_role_grant (
    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
    valid_from, source_code, approval_ref, created_at
)
SELECT 'aa100000-0000-4000-8000-000000000527', 'tenant-local', tech.principal_id::text,
       'aa100000-0000-4000-8000-000000000501',
       'NETWORK', net.service_network_id::text,
       now() - interval '1 day', 'LOCAL_FIXTURE', 'local-only', now()
FROM net, tech
ON CONFLICT (grant_id) DO NOTHING;

WITH net AS (
    SELECT service_network_id FROM net_service_network
    WHERE tenant_id = 'tenant-local' AND network_code = 'JINAN-LIXIA'
), tech AS (
    SELECT principal_id FROM idn_person_profile
    WHERE tenant_id = 'tenant-local' AND employee_number = 'SO-TECH-044'
)
INSERT INTO auth_role_grant (
    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
    valid_from, source_code, approval_ref, created_at
)
SELECT 'aa100000-0000-4000-8000-000000000544', 'tenant-local', tech.principal_id::text,
       'aa100000-0000-4000-8000-000000000501',
       'NETWORK', net.service_network_id::text,
       now() - interval '1 day', 'LOCAL_FIXTURE', 'local-only', now()
FROM net, tech
ON CONFLICT (grant_id) DO NOTHING;

-- 现场执行能力（PROJECT scope）：表单/资料/文件按 projectCapability 评估，
-- 对齐 TechnicianPortalFeedPostgresIT 夹具的授权模型（form.read/visit.checkIn 均 PROJECT scope）。
INSERT INTO auth_role (
    role_id, tenant_id, role_code, role_name, role_status, created_at
) VALUES (
    'aa100000-0000-4000-8000-000000000701',
    'tenant-local', 'product-technician-field', '师傅现场执行（产品场景）', 'ACTIVE', now()
) ON CONFLICT (tenant_id, role_code) DO NOTHING;

INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
VALUES
    ('aa100000-0000-4000-8000-000000000701', 'form.read', now()),
    ('aa100000-0000-4000-8000-000000000701', 'form.submit', now()),
    ('aa100000-0000-4000-8000-000000000701', 'evidence.read', now()),
    ('aa100000-0000-4000-8000-000000000701', 'evidence.submit', now()),
    ('aa100000-0000-4000-8000-000000000701', 'file.upload', now()),
    ('aa100000-0000-4000-8000-000000000701', 'file.download', now()),
    ('aa100000-0000-4000-8000-000000000701', 'appointment.read', now()),
    ('aa100000-0000-4000-8000-000000000701', 'visit.read', now())
ON CONFLICT (role_id, capability_code) DO NOTHING;

INSERT INTO auth_role_grant (
    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
    valid_from, source_code, approval_ref, created_at
)
SELECT grants.grant_id, 'tenant-local', tech.principal_id::text,
       'aa100000-0000-4000-8000-000000000701',
       'PROJECT', proj.project_id::text, now() - interval '1 day', 'LOCAL_FIXTURE', 'local-only', now()
FROM (
    VALUES
        ('aa100000-0000-4000-8000-000000000731'::uuid, 'SO-TECH-031'::text),
        ('aa100000-0000-4000-8000-000000000727'::uuid, 'SO-TECH-027'::text),
        ('aa100000-0000-4000-8000-000000000744'::uuid, 'SO-TECH-044'::text)
) AS grants(grant_id, employee_number)
JOIN idn_person_profile tech
  ON tech.tenant_id = 'tenant-local' AND tech.employee_number = grants.employee_number
JOIN prj_project proj
  ON proj.tenant_id = 'tenant-local' AND proj.project_code = 'BYD-OCEAN-SD-PILOT'
ON CONFLICT (grant_id) DO NOTHING;

-- 任务执行能力（TENANT scope）：人工任务 claim/start/complete 的授权按 TENANT 维度评估，
-- 细粒度安全由候选/责任快照与 claimedBy 状态机保证（与师傅整改任务自领取同一模型）。
-- file.upload/file.download 是通用文件子系统能力，DefaultFileCommandService 按 TENANT 维度
-- 校验（业务细粒度由 evidence.submit@PROJECT 等调用方保证）；师傅上传资料必须具备，
-- 否则受限上传会话创建失败关闭。
INSERT INTO auth_role (
    role_id, tenant_id, role_code, role_name, role_status, created_at
) VALUES (
    'aa100000-0000-4000-8000-000000000601',
    'tenant-local', 'product-technician-task', '师傅任务执行（产品场景）', 'ACTIVE', now()
) ON CONFLICT (tenant_id, role_code) DO NOTHING;

INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
VALUES
    ('aa100000-0000-4000-8000-000000000601', 'task.claim', now()),
    ('aa100000-0000-4000-8000-000000000601', 'task.start', now()),
    ('aa100000-0000-4000-8000-000000000601', 'task.complete', now()),
    ('aa100000-0000-4000-8000-000000000601', 'file.upload', now()),
    ('aa100000-0000-4000-8000-000000000601', 'file.download', now())
ON CONFLICT (role_id, capability_code) DO NOTHING;

INSERT INTO auth_role_grant (
    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
    valid_from, source_code, approval_ref, created_at
)
SELECT grants.grant_id, 'tenant-local', tech.principal_id::text,
       'aa100000-0000-4000-8000-000000000601',
       'TENANT', 'tenant-local', now() - interval '1 day', 'LOCAL_FIXTURE', 'local-only', now()
FROM (
    VALUES
        ('aa100000-0000-4000-8000-000000000631'::uuid, 'SO-TECH-031'::text),
        ('aa100000-0000-4000-8000-000000000627'::uuid, 'SO-TECH-027'::text),
        ('aa100000-0000-4000-8000-000000000644'::uuid, 'SO-TECH-044'::text)
) AS grants(grant_id, employee_number)
JOIN idn_person_profile tech
  ON tech.tenant_id = 'tenant-local' AND tech.employee_number = grants.employee_number
ON CONFLICT (grant_id) DO NOTHING;

-- 授权版本推进，强制后续请求重新解析授权快照
UPDATE auth_tenant_grant_generation
SET generation = generation + 1, updated_at = now()
WHERE tenant_id = 'tenant-local';
