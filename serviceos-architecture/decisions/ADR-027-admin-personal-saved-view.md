---
title: ADR-027：Admin 个人 SavedView 归属 readmodel 与最小授权
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Admin Portal Owner
related_adrs:
  - decisions/ADR-001-modular-monolith-first.md
  - decisions/ADR-013-reference-engineering-profile-and-module-enforcement.md
  - decisions/ADR-026-portal-context-navigation-in-authorization.md
---

# ADR-027：Admin 个人 SavedView 归属 readmodel 与最小授权

## 1. 状态与已接受决策

本 ADR 作为 M189 的边界与授权结论，正式接受：

1. Admin **个人** SavedView 由现有模块 `readmodel` 拥有（表 `rdm_saved_view`）；**不**新建
   preference 模块，也**不**把偏好写入 `authorization`；
2. HTTP 契约仅接受个人 CRUD：`GET/POST /api/v1/me/saved-views`、
   `PUT/DELETE /api/v1/me/saved-views/{id}`；**不**接受 share 端点；
3. **授权选择（最小）**：任意已认证主体可 CRUD **自己的**视图（principal + tenant 作用域）；
   不新增 `preference.manageSavedView` / `preference.readSavedView` Capability。
   列出或应用视图**不**授予页面或数据能力；业务列表/队列仍按各自 Capability 与 Scope 重新鉴权；
4. Filter AST 仅允许对应 pageId 已 Accepted 的 OpenAPI query 字段与 `EQ` 操作符；未知字段
   `QUERY_FILTER_NOT_ALLOWED`；页面筛选目录不兼容时 `SAVED_VIEW_SCHEMA_OUTDATED`；
5. 首批 pageId：`ADMIN.TASK.QUEUE`、`ADMIN.WORKORDER.LIST`、`ADMIN.CORRECTION.QUEUE`；
6. 明确不做：角色/组织共享视图、UI Preference、通用 work-queues、Network/Technician SavedView。

## 2. 上下文

API-06 §8 / DATA-06 §6 长期为草案。Admin Pilot 与身份治理完成后，个人 SavedView 是改善运营
目录/队列筛选体验的最小可靠切片，且不改变领域授权真相。偏好是用户拥有的真实数据，适合放在
`readmodel`（应用投影/偏好边界），避免扩大 authorization 职责。

## 3. 后果

- ArchitectureTest 继续验证 `readmodel` 仅依赖已声明的 `identity::api` 等；无需新增 Capability
  种子或授权治理 UI；
- RoleGrant 撤销后，SavedView 行可保留，但页面导航与业务查询仍 403；
- 共享视图若未来需要，必须另接受 API-06 share、独立能力与数据模型，不得在本切片静默扩展。
