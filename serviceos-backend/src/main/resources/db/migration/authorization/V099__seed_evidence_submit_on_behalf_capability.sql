-- M201：Network Portal 资料代补（onBehalf）入口能力（NETWORK scope 授予）。

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('evidence.submitOnBehalf', 'Network Portal 代师傅补传资料', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;
