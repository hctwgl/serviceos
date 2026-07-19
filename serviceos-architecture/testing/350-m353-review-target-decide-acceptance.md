---
title: M353 targetDecisions 裁决验收矩阵
status: Implemented
milestone: M353
lastUpdated: 2026-07-19
---

# M353 targetDecisions 裁决验收矩阵

| ID | 级别 | 场景 | 证据 |
|---|---|---|---|
| M353-01 | P0 | APPROVED 幂等重放不产生重复决定 | `ReviewCasePostgresIT#createsApprovesAndReplaysWithoutDuplicateDecisions` |
| M353-02 | P0 | REJECTED 自动创建 CorrectionCase | `CorrectionCasePostgresIT` |
| M353-03 | P0 | If-Match / aggregateVersion 冲突失败关闭 | decide 实现 + VERSION_CONFLICT |
| M353-04 | P0 | RULE 门禁阻断 APPROVED | `ReviewRuleGatePostgresIT` |
| M353-05 | P0 | 客户端不提交 overallDecision | OpenAPI DecideReviewCaseRequest |
| M353-06 | P1 | Admin 终审工作台可提交 | `FinalReviewWorkspace.vue` |
