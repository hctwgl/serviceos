-- M46: StoredFile 受控作废能力（状态机 AVAILABLE→INVALIDATED 已在 V010 约束中）。

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES
    ('file.invalidate', '作废已可用文件并阻断下载授权', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;
