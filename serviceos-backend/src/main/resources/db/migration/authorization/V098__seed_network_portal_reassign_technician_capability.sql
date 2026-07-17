-- M200：Network Portal 同网点改派师傅入口能力（NETWORK scope 授予）。

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('networkPortal.reassignTechnician', 'Network Portal 为本网点任务改派师傅', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;
