-- M282：配置设计器草稿与发布能力。

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES
    ('configuration.draft.write', '编辑配置资产草稿', 'NORMAL', now()),
    ('configuration.publish', '发布配置资产', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;
