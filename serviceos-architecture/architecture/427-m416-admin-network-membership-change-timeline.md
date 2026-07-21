---
title: M416 Admin 网点任职并入主体变更时间线
version: 0.1.0
status: Implemented
milestone: M416
lastUpdated: 2026-07-21
---

# M416 Admin 网点任职并入主体变更时间线

## 1. 目标

关闭 M415 剩余 UI_DATA_GAP「网点任职并入主体时间线」：在用户详情变更时间线中 soft-gate 合并
`NetworkMembership` 不可变目录事件，并保持模块边界与缺权诚实省略。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.82** `NETWORK_MEMBERSHIP` source；`omittedSources` 增加同枚举 |
| Network | `NetworkMembershipChangeTimelineContributor` ← `net_directory_event` |
| Identity | 既有 soft-gate 合并路径自动收录新 Contributor |
| Admin Web | 「网点任职」来源标签与缺权 omitted 提示 |
| 证据 | `IdentityDirectoryPostgresIT` + ArchitectureTest + Playwright |

## 3. 权限

- 硬门禁：`identity.read`
- soft-gate：`network.read`（`NETWORK_MEMBERSHIP`）

## 4. 明确未实现

- 师傅服务关系（`TECHNICIAN_MEMBERSHIP_*`）并入主体时间线
- 通用 `AUTHORIZATION_DENIED` 作为主体活动流
- 全国区县全量树 / 拼音索引 / 多级子品牌（缺权威数据集，另立里程碑）
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
