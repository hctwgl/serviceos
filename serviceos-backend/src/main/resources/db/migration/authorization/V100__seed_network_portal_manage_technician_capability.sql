-- M204：Network Portal 本网点师傅关系与资质提交入口能力（NETWORK scope 授予）。

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('networkPortal.manageTechnician', 'Network Portal 管理本网点师傅关系与资质提交', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;
