---
title: M415 Admin 跨聚合主体变更时间线
version: 0.1.0
status: Implemented
milestone: M415
lastUpdated: 2026-07-21
---

# M415 Admin 跨聚合主体变更时间线

## 1. 目标

关闭 M405 剩余 UI_DATA_GAP：在用户详情变更时间线中合并任职与 RoleGrant 事实，并解析操作者显示名。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.81** `MEMBERSHIP`/`ROLE_GRANT` source；`actorDisplayName`；`omittedSources` |
| SPI | `PrincipalChangeTimelineContributor`（identity::api） |
| Organization | `MembershipChangeTimelineContributor` ← `org_structure_event` |
| Authorization | `RoleGrantChangeTimelineContributor` ← `auth_role_grant_event` |
| Identity | soft-gate 合并贡献源 + `PrincipalPersonaQuery.displayNames` |
| Admin Web | 变更记录展示任职/授权来源、操作者显示名、缺权 omitted 提示 |

## 3. 权限

- 硬门禁：`identity.read`
- soft-gate：`organization.read`（MEMBERSHIP）、`authorization.read`（ROLE_GRANT）

## 4. 明确未实现

- 网点任职（Network membership）并入主体时间线（已由 **M416** 关闭主路径）
- 通用 AUTHORIZATION_DENIED 作为主体活动流
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
