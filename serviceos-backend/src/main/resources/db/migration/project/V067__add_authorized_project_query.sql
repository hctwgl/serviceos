INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('project.read', '读取授权项目目录与范围历史', 'NORMAL', now());

-- 目录游标固定使用 project_code/project_id；索引首列 tenant_id 保证任何查询都不能跨租户扫描。
CREATE INDEX ix_prj_project_directory_cursor
    ON prj_project (tenant_id, project_code, project_id)
    INCLUDE (client_id, project_name, starts_on, ends_on, project_status, aggregate_version, created_at);
