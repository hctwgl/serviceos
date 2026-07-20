---
title: M405 Admin 主体变更时间线
version: 0.1.0
status: Implemented
milestone: M405
lastUpdated: 2026-07-20
---

# M405 Admin 主体变更时间线

## 1. 目标

关闭用户详情「变更记录」UI_DATA_GAP：提供生命周期 + 审计 + 登录聚合时间线。

## 2. 已实现

| 层 | 内容 |
|---|---|
| Audit | `AuditQueryService.listByTarget` |
| Identity | `GET /security-principals/{id}/change-timeline`（`identity.read`） |
| OpenAPI | **1.0.71** `PrincipalChangeTimelinePage` |
| Admin Web | 变更记录 Tab 时间线（保留待重分配区） |

## 3. 明确未实现

- 跨聚合（任职/RoleGrant）统一业务时间线
- 操作者显示名解析（当前短截断/系统登记）
- 产品负责人视觉金标

## 4. 去重规则

生命周期与登录专用源已覆盖的动作，不再重复展示同名审计行。
