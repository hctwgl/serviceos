---
title: M330 RULE 接入 Task complete / Evidence Snapshot 门禁
status: Implemented
milestone: M330
lastUpdated: 2026-07-19
relatedMilestones: [M325, M329, M308, M41]
---

# M330 RULE 接入 Task complete / Evidence Snapshot 门禁

## 目标

HUMAN Task 冻结 `ruleRef` 后，`EvidenceSetSnapshot`（`TASK_SUBMISSION`）创建与
`task.complete` 前用冻结 Bundle RULE 失败关闭；BLOCK / REQUIRE_APPROVAL 拒绝前进
（此处无 forceApprove 旁路）。

## 范围与非目标

- 范围：
  - 抽取 `FrozenTaskRuleEvaluator`，供 M325/M329 Review 门禁与本切片复用
  - `TaskFulfillmentRuleGate` + `RuleTaskCompletionValidator`
  - `DefaultEvidenceSetSnapshotService` 在 `TASK_SUBMISSION` 创建前调用门禁
  - `HumanTaskCompletionValidator` 增加 correlationId 默认方法
  - PostgreSQL IT：`TaskFulfillmentRuleGatePostgresIT`
- 明确不做：
  - 单条 Evidence upload finalize 的 RULE 门禁
  - `clientRuleRef` / FORM_REVIEW / WORK_ORDER_REVIEW subjectType
  - formValues 条件
  - OpenAPI / Flyway 变更

## 已实现

- Snapshot 创建 + Task complete RULE 门禁 + IT；Review 门禁委托共享求值器

## 明确未实现

- Mapping Profile 硬编码拆除、defaults/enum/condition DSL
- DISPATCH TECHNICIAN 自动指派
- 低代码深化、吉利联调

## 验证命令

```bash
bash scripts/agent-verify.sh it TaskFulfillmentRuleGatePostgresIT
bash scripts/agent-verify.sh it ReviewRuleGatePostgresIT,ClientReviewRuleGatePostgresIT
bash scripts/agent-verify.sh test ArchitectureTest
```
