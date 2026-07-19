---
title: M330 Task complete / Evidence Snapshot RULE 门禁验收矩阵
status: Implemented
milestone: M330
lastUpdated: 2026-07-19
---

# M330 Task complete / Evidence Snapshot RULE 门禁验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M330-01 | TASK_SUBMISSION Snapshot + RULE BLOCK | `REVIEW_RULE_BLOCKED`；无快照行；拒绝审计落库 | `TaskFulfillmentRuleGatePostgresIT` |
| M330-02 | Task complete + RULE BLOCK | `REVIEW_RULE_BLOCKED`；Task 仍 RUNNING | 同上 |
| M330-03 | 无 ruleRef | Snapshot/complete 行为不变 | 既有 HumanTask / Snapshot IT |
| M330-04 | INTERNAL/CLIENT decide 回归 | M325/M329 门禁仍成立 | `ReviewRuleGatePostgresIT` / `ClientReviewRuleGatePostgresIT` |
| M330-05 | 模块边界 | ArchitectureTest | ArchitectureTest |

## 明确不验收

- 单条 upload finalize RULE、forceApprove 旁路、OpenAPI、吉利联调
