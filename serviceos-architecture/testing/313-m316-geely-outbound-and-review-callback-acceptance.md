---
title: M316 吉利出站/回调验收矩阵
status: Implemented
milestone: M316
lastUpdated: 2026-07-19
---

# M316 吉利出站/回调验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| G316-01 | prepare+send+interpret | LOCAL_STUB ACCEPTED | `GeelyOutboundSubmissionConnectorTest` |
| G316-02 | Profile lineage | 仅认领 geely-haohan-v1.3-local CREATE | 同上 |
| G316-03 | 多 Profile 路由 | 精确 mapping 命中；未知失败关闭 | `OutboundReviewSubmissionProfilesTest` |
| G316-04 | ReviewCase 回调路由 IT | BYD 精确 mapping 仍通过 | `ReviewCasePostgresIT` |
