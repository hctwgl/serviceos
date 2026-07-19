---
title: M301 ExternalReviewRoute Profile 解析验收矩阵
status: Implemented
milestone: M301
lastUpdated: 2026-07-19
---

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M301-01 | 精确 mapping 命中 | 返回对应 Profile | OutboundReviewSubmissionProfilesTest |
| M301-02 | 未知 mapping + 单 Profile | 回退唯一 Profile | OutboundReviewSubmissionProfilesTest |
| M301-03 | ReviewCase 回调回归 | 既有 BYD 回调 IT 通过 | ReviewCasePostgresIT |
