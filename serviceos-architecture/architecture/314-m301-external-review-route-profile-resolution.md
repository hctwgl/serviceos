---
title: M301 ExternalReviewRoute Profile 解析
status: Implemented
milestone: M301
lastUpdated: 2026-07-19
relatedMilestones: [M55, M299, M300]
---

# M301 ExternalReviewRoute Profile 解析

## 目标

去掉 `DefaultExternalReviewRouteService` 中硬编码的 BYD connectorVersion，改为经
`OutboundReviewSubmissionProfiles` 解析。

## 范围

- `requireForRouteRegistration`：精确匹配 callback mapping；零命中且仅单 Profile 时回退；多 OEM 多命中/零命中失败关闭。
- ReviewCasePostgresIT 回归。

## 明确未实现

Update 入站、远端查询、INTEGRATION Mapping 运行时。

## 验证

```bash
bash scripts/agent-verify.sh test OutboundReviewSubmissionProfilesTest
bash scripts/agent-verify.sh it ReviewCasePostgresIT
```
