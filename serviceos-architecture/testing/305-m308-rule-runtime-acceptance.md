---
title: M308 RULE 运行时验收矩阵
status: Implemented
milestone: M308
lastUpdated: 2026-07-19
---

# M308 RULE 运行时验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| R308-01 | WARN + BLOCK 同时命中 | decision=BLOCK；hits 含两者 | `DefaultRuleRuntimeTest#aggregatesBlockOverWarnings` |
| R308-02 | 仅 WARN | PASS_WITH_WARNINGS | `DefaultRuleRuntimeTest#returnsPassWithWarningsWhenOnlyWarnHits` |
| R308-03 | REQUIRE_APPROVAL | REQUIRE_APPROVAL | `DefaultRuleRuntimeTest#returnsRequireApprovalWhenNoBlock` |
| R308-04 | 无命中 | defaultAction（REQUIRE_MANUAL） | `DefaultRuleRuntimeTest#usesDefaultActionWhenNoRuleHits` |
| R308-05 | subjectType/stage 不匹配 | VALIDATION_FAILED | `DefaultRuleRuntimeTest#failsClosedOnSubjectStageMismatch` |
| R308-06 | ruleKey 缺失 | RESOURCE_NOT_FOUND | `DefaultRuleRuntimeTest#failsClosedWhenRuleKeyMissing` |
| R308-07 | 冻结 Bundle PostgreSQL | BLOCK + hit ruleCode | `RuleRuntimePostgresIT#resolvesDecisionFromFrozenRuleSet` |
