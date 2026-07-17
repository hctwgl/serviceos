-- 仅用于本地开发。先启动后端让 Flyway 建表，再执行本脚本。
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
    ('bf64aa35-11cb-40bc-b301-10b5853049b3', 'file.download', now())
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
