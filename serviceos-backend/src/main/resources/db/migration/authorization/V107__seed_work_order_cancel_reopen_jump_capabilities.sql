-- M279：工单取消/重开与流程人工跳转能力。

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES
    ('workOrder.cancel', '取消工单', 'HIGH', now()),
    ('workOrder.reopen', '重开已取消工单', 'HIGH', now()),
    ('workflow.jump', '流程人工跳转', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;
