-- M196：Network Portal 指派师傅入口能力（NETWORK scope 授予；不授予 Admin TENANT 派单权）。

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('networkPortal.assignTechnician', 'Network Portal 为本网点任务指派师傅', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;
