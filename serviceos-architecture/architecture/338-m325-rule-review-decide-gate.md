---
title: M325 RULE 接入 INTERNAL ReviewCase.decide 门禁
status: Implemented
milestone: M325
lastUpdated: 2026-07-19
relatedMilestones: [M44, M48, M308, M324]
---

# M325 RULE 接入 INTERNAL ReviewCase.decide 门禁

## 目标

HUMAN Task 冻结 `ruleRef` 后，INTERNAL `ReviewCase.decide(APPROVED)` 前用冻结 Bundle RULE
失败关闭求值；BLOCK / REQUIRE_APPROVAL 拒绝通过，REJECTED 与 forceApprove 不受阻。

## 范围与非目标

- 范围：
  - Flyway `V123`：`tsk_task.rule_ref`
  - `WorkflowDefinitionParser` 贯通 `ruleRef`
  - `ReviewRuleGate`：`EVIDENCE_REVIEW` / `INTERNAL` + WorkOrder 表达式上下文
  - `DefaultReviewCaseService.decide` 在 `markDecided` 前调用门禁
  - 拒绝审计独立事务提交（`ReviewRuleDenyAuditor` REQUIRES_NEW）
  - ProblemCode：`REVIEW_RULE_BLOCKED` / `REVIEW_RULE_REQUIRES_APPROVAL`
- 明确不做：
  - CLIENT / external receipt 门禁
  - Evidence finalize / Task complete 门禁
  - formValues 条件
  - 自动写 ReviewDecision / CorrectionCase
  - OpenAPI 变更

## 已实现

- 冻结 `ruleRef` + decide 门禁 + PostgreSQL IT

## 明确未实现

- CLIENT 外部回执 RULE 门禁：见 **M329**
- Evidence finalize / Task complete RULE 门禁
- FORM_REVIEW / WORK_ORDER_REVIEW subjectType

## 验证命令

```bash
bash scripts/agent-verify.sh it ReviewRuleGatePostgresIT
bash scripts/agent-verify.sh test ArchitectureTest
```
