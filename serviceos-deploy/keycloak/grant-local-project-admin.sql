-- 仅用于本地开发。先启动后端让 Flyway 建表，再执行本脚本。
-- 统一主体目录使用 Keycloak 用户稳定 id 作为预配 Principal id；issuer/subject 绑定后，
-- Resource Server 每次请求都必须先解析该绑定并检查主体状态，不能继续把 JWT subject 直接当授权主体。
INSERT INTO idn_security_principal (
    principal_id, tenant_id, principal_type, principal_status,
    aggregate_version, created_at, updated_at
) VALUES (
    '06b612f3-a901-4b0e-bd90-86b4259cc087', 'tenant-local', 'USER', 'ACTIVE', 1, now(), now()
) ON CONFLICT (principal_id) DO NOTHING;

INSERT INTO idn_person_profile (
    principal_id, tenant_id, display_name, employee_number,
    profile_version, created_at, updated_at, updated_by
) VALUES (
    '06b612f3-a901-4b0e-bd90-86b4259cc087', 'tenant-local', 'Local Developer',
    'LOCAL-DEVELOPER', 1, now(), now(), 'local-fixture'
) ON CONFLICT (principal_id) DO NOTHING;

INSERT INTO idn_identity_link (
    identity_link_id, tenant_id, principal_id, issuer, subject_value,
    client_id, linked_by, linked_at
) VALUES (
    '4b6d6352-a4da-4ead-9424-0dc32f7c9279', 'tenant-local',
    '06b612f3-a901-4b0e-bd90-86b4259cc087',
    'http://localhost:8081/realms/serviceos', '06b612f3-a901-4b0e-bd90-86b4259cc087',
    'serviceos-local-cli', 'local-fixture', now()
) ON CONFLICT (tenant_id, issuer, subject_value) DO NOTHING;

INSERT INTO idn_principal_lifecycle_event (
    lifecycle_event_id, tenant_id, principal_id, event_type, principal_version,
    reason, actor_id, request_digest, correlation_id, occurred_at
) VALUES (
    'b7df24e5-e9c9-4199-bcce-f651f6ff4a01', 'tenant-local',
    '06b612f3-a901-4b0e-bd90-86b4259cc087', 'REGISTERED', 1,
    'LOCAL_FIXTURE', 'local-fixture', repeat('0', 64), 'local-fixture', now()
) ON CONFLICT (lifecycle_event_id) DO NOTHING;

-- M188：ADMIN 上下文要求有效 INTERNAL_EMPLOYEE Persona + RoleGrant。
INSERT INTO idn_principal_persona (
    persona_id, tenant_id, principal_id, persona_type, persona_status,
    valid_from, valid_to, persona_version, created_by, created_at
) VALUES (
    '8c0d1e2f-3a4b-4c5d-8e6f-7a8b9c0d1e2f',
    'tenant-local', '06b612f3-a901-4b0e-bd90-86b4259cc087',
    'INTERNAL_EMPLOYEE', 'ACTIVE', now() - interval '1 day', NULL, 1,
    'local-fixture', now()
) ON CONFLICT (tenant_id, principal_id, persona_type) DO NOTHING;

INSERT INTO auth_tenant_grant_generation (tenant_id, generation, updated_at)
VALUES ('tenant-local', 1, now())
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO auth_role (
    role_id, tenant_id, role_code, role_name, role_status, created_at
) VALUES (
    'bf64aa35-11cb-40bc-b301-10b5853049b3',
    'tenant-local', 'local-project-admin', '本地项目管理员', 'ACTIVE', now()
) ON CONFLICT (tenant_id, role_code) DO NOTHING;

INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
VALUES
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'project.create', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'project.read', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'project.team.manage', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'workOrder.read', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'task.read', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'task.assign', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'task.claim', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'task.start', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'task.complete', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'task.release', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'sla.read', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'appointment.read', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'appointment.propose', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'appointment.manage', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'appointment.cancel', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'appointment.recordContact', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'visit.read', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'visit.checkIn', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'visit.checkOut', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'visit.interrupt', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'form.read', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'form.submit', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'evidence.read', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'evidence.submit', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'evidence.review', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'evidence.forceApprove', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'evidence.waiveCorrection', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'review.reopen', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'dispatch.read', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'dispatch.assignment.manage', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'dispatch.capacity.configure', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'integration.readInbound', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'integration.readOutbound', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'integration.submitClientReview', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'operations.exception.read', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'file.upload', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'file.download', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'identity.read', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'identity.readSensitive', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'identity.register', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'identity.manageLinks', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'identity.manageLifecycle', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'identity.manageProfile', now()),
    -- M187 Admin 统一用户中心：组织 / 网点 / 授权治理能力
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'organization.read', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'organization.manageStructure', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'organization.manageMembership', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'organization.sync', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'organization.overrideExternal', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'network.read', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'network.managePartner', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'network.manageNetwork', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'network.manageMembership', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'network.manageTechnician', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'network.reviewQualification', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'authorization.read', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'authorization.manageRoles', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'authorization.requestGrant', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'authorization.approveGrant', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'authorization.revokeGrant', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'authorization.delegate', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'authorization.explain', now()),
    -- M191 Admin 共享 SavedView
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'preference.shareSavedView', now()),
    -- M192 Admin 受控全局搜索
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'search.read', now()),
    -- M378+ 项目履约配置
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'project.fulfillment.read', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'project.fulfillment.create', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'project.fulfillment.draft.write', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'project.fulfillment.validate', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'project.fulfillment.publish', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'project.fulfillment.suspend', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'project.fulfillment.resume', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'project.fulfillment.revision.read', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'project.fulfillment.snapshot.read', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'project.fulfillment.techRef.read', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'pricing.snapshot.read', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'configuration.draft.write', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'configuration.approve', now()),
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'configuration.publish', now())
ON CONFLICT (role_id, capability_code) DO NOTHING;

INSERT INTO auth_role_grant (
    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
    valid_from, source_code, approval_ref, created_at
) VALUES (
    'c8bf90fe-0337-46ee-a630-31fb615246f6',
    'tenant-local', '06b612f3-a901-4b0e-bd90-86b4259cc087',
    'bf64aa35-11cb-40bc-b301-10b5853049b3',
    'TENANT', 'tenant-local', now(), 'LOCAL_FIXTURE', 'local-only', now()
) ON CONFLICT (grant_id) DO NOTHING;

-- M187 低权限 viewer：Keycloak create 不保证自定义 UUID，由
-- 本地产品场景重置按 Keycloak 实际 subject 幂等回填。

-- BYD 适配器 SERVICE 主体：外发 HTTP 成功后同租户落 CLIENT Case / Route。
INSERT INTO auth_role (
    role_id, tenant_id, role_code, role_name, role_status, created_at
) VALUES (
    'a1c3e5f7-0911-4b2d-8e3f-5a6b7c8d9e0f',
    'tenant-local', 'local-byd-cpim-adapter', '本地 BYD 适配器', 'ACTIVE', now()
) ON CONFLICT (tenant_id, role_code) DO NOTHING;

INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
VALUES
    ('a1c3e5f7-0911-4b2d-8e3f-5a6b7c8d9e0f', 'evidence.createClientReviewCase', now()),
    ('a1c3e5f7-0911-4b2d-8e3f-5a6b7c8d9e0f', 'evidence.recordExternalReceipt', now()),
    ('a1c3e5f7-0911-4b2d-8e3f-5a6b7c8d9e0f', 'evidence.read', now()),
    ('a1c3e5f7-0911-4b2d-8e3f-5a6b7c8d9e0f', 'integration.registerExternalReviewRoute', now()),
    ('a1c3e5f7-0911-4b2d-8e3f-5a6b7c8d9e0f', 'integration.submitClientReview', now()),
    ('a1c3e5f7-0911-4b2d-8e3f-5a6b7c8d9e0f', 'integration.readOutbound', now())
ON CONFLICT (role_id, capability_code) DO NOTHING;

INSERT INTO auth_role_grant (
    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
    valid_from, source_code, approval_ref, created_at
) VALUES (
    'd0e1f2a3-b4c5-4678-9012-3456789abcde',
    'tenant-local', 'service-byd-cpim-adapter',
    'a1c3e5f7-0911-4b2d-8e3f-5a6b7c8d9e0f',
    'TENANT', 'tenant-local', now(), 'LOCAL_FIXTURE', 'local-only', now()
) ON CONFLICT (grant_id) DO NOTHING;
