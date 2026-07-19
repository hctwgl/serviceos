---
title: M329 RULE 接入 CLIENT 外部回执门禁
status: Implemented
milestone: M329
lastUpdated: 2026-07-19
relatedMilestones: [M55, M325, M328]
---

# M329 RULE 接入 CLIENT 外部回执门禁

## 目标

CLIENT `ExternalReviewReceipt.record(APPROVED)` 前复用 Task 冻结 `ruleRef` 与
`ReviewRuleGate` 失败关闭；REJECTED 回执不受阻。

## 范围与非目标

- 范围：
  - `DefaultExternalReviewReceiptService.record` 在 `markDecided` 前调用 `ReviewRuleGate`
  - 解析仍使用资产 `subjectType=EVIDENCE_REVIEW` / `stage=INTERNAL`（与 Task `ruleRef` 同一资产）
  - 复用 `REVIEW_RULE_BLOCKED` / `REVIEW_RULE_REQUIRES_APPROVAL` 与拒绝审计 REQUIRES_NEW
  - PostgreSQL IT：`ClientReviewRuleGatePostgresIT`
- 明确不做：
  - `clientRuleRef` 双阶段冻结列
  - Evidence Snapshot / Task complete RULE 门禁（见 **M330**）
  - formValues 条件
  - OpenAPI / Flyway 变更

## 已实现

- CLIENT APPROVED 回执 RULE 门禁 + IT

## 明确未实现

- Evidence Snapshot / Task complete RULE 门禁：见 **M330**
- 独立 CLIENT-stage 资产、吉利联调

## 验证命令

```bash
bash scripts/agent-verify.sh it ClientReviewRuleGatePostgresIT
bash scripts/agent-verify.sh test ArchitectureTest
```
