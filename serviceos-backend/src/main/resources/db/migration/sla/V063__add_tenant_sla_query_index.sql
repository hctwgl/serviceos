-- M63：TENANT RoleGrant 的跨项目 SLA 队列仍按稳定游标顺序读取，避免租户级全表排序。
CREATE INDEX idx_sla_instance_tenant_deadline_cursor
    ON sla_instance (tenant_id, deadline_at, sla_instance_id);
