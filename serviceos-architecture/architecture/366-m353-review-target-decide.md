---
title: M353 ReviewCase targetDecisions 正式裁决
status: Implemented
milestone: M353
lastUpdated: 2026-07-19
relatedMilestones: [M351, M352, M44, M325]
openapiVersion: "1.0.49"
flywayVersion: "130"
---

# M353 ReviewCase targetDecisions 正式裁决

## 目标

将 `POST /review-cases/{id}:decide` 演进为 Accepted 契约的 `targetDecisions` 模型：
客户端不得提交 `overallDecision`；服务端按冻结 Snapshot 派生整组结果；If-Match 绑定
`aggregateVersion`；同事务写入 `ReviewTargetDecision` 并在 REJECTED 时创建 Correction。

## 范围

- OpenAPI **1.0.49**：`DecideReviewCaseRequest.targetDecisions` + If-Match
- Flyway **V130**：`aggregate_version` + `evd_review_target_decision`
- `DefaultReviewCaseService.decide` 完整命令链
- Admin 终审工作台正式提交 + Modal 确认
- ReviewCase / Correction / ReviewRuleGate PostgresIT

## 明确未实现

独立审核 HUMAN Task 与提交 Task 分离的工作流模板编排；空 Snapshot 的 reject 派生仅用于 RULE 门禁夹具。

## 验证

```bash
bash scripts/agent-verify.sh it ReviewCasePostgresIT,CorrectionCasePostgresIT,ReviewRuleGatePostgresIT
cd serviceos-admin-web && npm run build
```
