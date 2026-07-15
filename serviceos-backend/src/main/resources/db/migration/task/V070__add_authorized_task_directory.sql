INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('task.read', '读取授权任务队列与详情', 'NORMAL', now());

CREATE INDEX ix_tsk_task_directory_scope_cursor
    ON tsk_task (tenant_id, project_id, priority DESC, next_run_at, created_at, task_id);
