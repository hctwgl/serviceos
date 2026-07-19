---
title: M329 CLIENT 外部回执 RULE 门禁验收矩阵
status: Implemented
milestone: M329
lastUpdated: 2026-07-19
---

# M329 CLIENT 外部回执 RULE 门禁验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M329-01 | CLIENT APPROVED + RULE BLOCK | `REVIEW_RULE_BLOCKED`；Case 仍 OPEN；无回执行；拒绝审计落库 | `ClientReviewRuleGatePostgresIT` |
| M329-02 | CLIENT REJECTED + RULE BLOCK | 允许 REJECTED | 同上 |
| M329-03 | 无 ruleRef | 回执行为不变 | 既有 ReviewCase IT |
| M329-04 | 模块边界 | ArchitectureTest | ArchitectureTest |

## 明确不验收

- Evidence finalize、Task complete、clientRuleRef、OpenAPI
