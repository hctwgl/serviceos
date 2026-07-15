INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('workOrder.read', '读取授权工单目录与详情', 'NORMAL', now());

CREATE INDEX ix_wo_work_order_tenant_project_received
    ON wo_work_order (tenant_id, project_id, received_at DESC, id DESC);
