---
title: M417 Admin 师傅服务关系并入主体变更时间线
version: 0.1.0
status: Implemented
milestone: M417
lastUpdated: 2026-07-21
---

# M417 Admin 师傅服务关系并入主体变更时间线

## 1. 目标

关闭 M416 剩余 UI_DATA_GAP「师傅服务关系并入主体时间线」：在用户详情变更时间线中 soft-gate 合并
`NetworkTechnicianMembership` 不可变目录事件。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.83** `TECHNICIAN_MEMBERSHIP` source；`omittedSources` 增加同枚举 |
| Network | `TechnicianMembershipChangeTimelineContributor` ← `net_directory_event` |
| Identity | 既有 soft-gate 合并路径自动收录；与网点任职共用 `network.read` |
| Admin Web | 「师傅服务关系」来源标签与缺权 omitted 提示 |
| 证据 | `IdentityDirectoryPostgresIT` + ArchitectureTest + Playwright |

## 3. 权限

- 硬门禁：`identity.read`
- soft-gate：`network.read`（`TECHNICIAN_MEMBERSHIP`；缺权时与 `NETWORK_MEMBERSHIP` 一并 omitted）

## 4. 明确未实现

- 通用 `AUTHORIZATION_DENIED` 作为主体活动流（高流量，不宜直接混入变更时间线）
- 师傅档案创建/停用生命周期事件并入（本切片仅服务关系）
- 全国区县全量树 / 拼音索引 / 多级子品牌
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
