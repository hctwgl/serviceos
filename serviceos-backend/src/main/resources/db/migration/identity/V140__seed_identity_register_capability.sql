-- M402：Admin 正式登记主体（不保存密码；OIDC 绑定仍走 identity.manageLinks）。

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('identity.register', '登记统一主体（无密码）', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;
