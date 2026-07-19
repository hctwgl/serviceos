---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR #148～#156：M321～M329 Draft stacked（base 链至 master）
- PR #157：待创建 — **M330** RULE Task complete / Evidence Snapshot 门禁（Draft，base=#156）
- `master`：`32b902f897d19d2c906acac899990bf1aa2bb056`（PR #147 已合并）
- latestMilestone：**M330**
- Flyway：**125**；OpenAPI：**1.0.43**（本切片无契约/迁移变更）

## 本回合完成

### M330 RULE → Task complete / Evidence Snapshot 门禁

- 抽取 `FrozenTaskRuleEvaluator`（M325/M329/M330 共用）
- `TaskFulfillmentRuleGate` + `RuleTaskCompletionValidator`
- `DefaultEvidenceSetSnapshotService` 在 `TASK_SUBMISSION` 创建前失败关闭
- IT：`TaskFulfillmentRuleGatePostgresIT`；回归 M325/M329 IT PASS
- 文档：`343-m330-*` / `327-m330-*`

### 既有 Draft 栈

- M321～M329（PR #148～#156）

## 验证

```text
bash scripts/agent-verify.sh it TaskFulfillmentRuleGatePostgresIT,ReviewRuleGatePostgresIT,ClientReviewRuleGatePostgresIT
bash scripts/agent-verify.sh test ArchitectureTest
```

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight

## 下一步

本地 Configuration-Driven Runtime 主线 RULE 门禁已覆盖 INTERNAL decide / CLIENT 回执 /
Snapshot 创建 / Task complete。后续优先：

1. Mapping：拆除 Profile 硬编码；defaults / enum / condition DSL
2. DISPATCH：TECHNICIAN 自动指派
3. 低代码深化；吉利材料齐备后联调
