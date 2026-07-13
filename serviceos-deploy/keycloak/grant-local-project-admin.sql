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
