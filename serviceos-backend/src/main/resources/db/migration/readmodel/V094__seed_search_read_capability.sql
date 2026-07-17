-- M192：Admin 受控全局搜索入口能力。搜索不授予数据能力；各 type 仍需 underlying 读能力。

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('search.read', 'Admin 受控全局搜索入口', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;
