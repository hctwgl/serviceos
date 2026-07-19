---
title: M325 RULE ReviewCase.decide 门禁验收矩阵
status: Implemented
milestone: M325
lastUpdated: 2026-07-19
---

# M325 RULE ReviewCase.decide 门禁验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M325-01 | Task 冻结 ruleRef | `tsk_task.rule_ref` | `ReviewRuleGatePostgresIT` |
| M325-02 | RULE BLOCK + APPROVED | `REVIEW_RULE_BLOCKED`；Case 仍 OPEN；拒绝审计落库 | 同上 |
| M325-03 | RULE BLOCK + REJECTED | 允许 REJECTED | 同上 |
| M325-04 | 无 ruleRef | decide 行为不变 | 既有 ReviewCase IT |
| M325-05 | forceApprove | 不经门禁（本切片不改 forceApprove） | 代码路径 |
| M325-06 | 模块边界 | ArchitectureTest | ArchitectureTest |

## 明确不验收

- CLIENT 路径、formValues、自动副作用、OpenAPI
