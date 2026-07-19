-- 产品可用性：Admin 工单工作区只读查看 SHADOW 定价试算快照（非正式结算）。

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('pricing.snapshot.read', '读取工单影子定价试算快照（非结算）', 'NORMAL', now())
ON CONFLICT (capability_code) DO NOTHING;
