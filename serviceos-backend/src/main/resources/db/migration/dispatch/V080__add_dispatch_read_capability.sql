-- M92：工作区只读服务责任使用独立 NORMAL capability，不复用高风险派单命令能力。

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('dispatch.read', '读取当前服务责任', 'NORMAL', now());
