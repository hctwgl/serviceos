-- M62：SLA 工作台与工单时间线按不可变 deadline/id 稳定翻页，不依赖运行时全表排序。

CREATE INDEX ix_sla_instance_project_deadline
    ON sla_instance (tenant_id, project_id, deadline_at, sla_instance_id);

CREATE INDEX ix_sla_instance_work_order_deadline
    ON sla_instance (tenant_id, project_id, work_order_id, deadline_at, sla_instance_id);

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('sla.read', '查看 SLA 时钟事实', 'NORMAL', now());

COMMENT ON INDEX ix_sla_instance_project_deadline IS
    'M62：显式 Project Scope 下的 SLA 工作台稳定游标索引';
COMMENT ON INDEX ix_sla_instance_work_order_deadline IS
    'M62：工单 SLA 时间线稳定游标索引';
