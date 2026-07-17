-- M197：Network Portal 预约协作入口能力（NETWORK scope 授予；不授予 Admin TENANT 预约权）。

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('networkPortal.manageAppointment', 'Network Portal 为本网点任务提议/确认预约', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;
