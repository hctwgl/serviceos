-- 产品可用性：Network Portal 网点接单（仅激活 ACTIVE NETWORK，不强制同时指派师傅）。

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('networkPortal.acceptAssignment', 'Network Portal 为本网点确认接单', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;
