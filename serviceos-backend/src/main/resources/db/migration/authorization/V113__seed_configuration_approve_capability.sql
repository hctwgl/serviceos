-- M285：配置草稿发布审批能力。

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('configuration.approve', '审批配置资产草稿', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;
