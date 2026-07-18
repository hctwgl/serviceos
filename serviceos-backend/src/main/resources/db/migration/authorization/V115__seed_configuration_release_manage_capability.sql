-- M286：配置发布通道（灰度/回滚）管理能力。

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('configuration.release.manage', '管理配置 Bundle 灰度与回滚', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;
