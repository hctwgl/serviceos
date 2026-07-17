---
title: ADR-029：Admin 共享 SavedView 可见性与能力门禁
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Admin Portal Owner
related_adrs:
  - decisions/ADR-001-modular-monolith-first.md
  - decisions/ADR-013-reference-engineering-profile-and-module-enforcement.md
  - decisions/ADR-025-role-grant-governance.md
  - decisions/ADR-027-admin-personal-saved-view.md
  - decisions/ADR-028-admin-ui-preferences.md
---

# ADR-029：Admin 共享 SavedView 可见性与能力门禁

## 1. 状态与已接受决策

本 ADR 作为 M191 的边界与授权结论，正式接受：

1. 共享 SavedView 仍由 `readmodel` 拥有（表 `rdm_saved_view`）；不新建 preference 模块；
2. HTTP：`POST /api/v1/saved-views/{id}:share`；取消共享通过同一端点 `visibility=PRIVATE`；
3. 可见性：`PRIVATE` / `ROLE`（`sharedScopeRef=roleId`）/ `TENANT`（租户级；不用
   `ORGANIZATION` 组织树，避免未决设计）；不接受 Network/Technician；
4. **能力**：分享到 `ROLE`/`TENANT` 需要 `preference.shareSavedView`（HIGH）；owner 收回
   `PRIVATE` 始终允许；列出/应用共享视图不授予页面或数据能力；
5. `readmodel` 允许依赖 `authorization::api`（`AuthorizationService`、
   `PrincipalActiveRoleQuery`）与 `audit::api`（高风险 share/unshare 审计）；
6. `GET /me/saved-views` 合并本人视图与当前主体可见共享视图；业务查询仍重新鉴权。

## 2. 上下文

M189 交付个人 SavedView 后，运营需要在同租户或角色范围内复用筛选定义。共享不得变成隐性
数据授权；ROLE 可见性绑定有效 RoleGrant，TENANT 仅扩大查询定义可见范围。

## 3. 后果

- ArchitectureTest 验证 `readmodel → authorization::api / audit::api`；
- 本地 fixture 须授予 `preference.shareSavedView` 方可 E2E 走通分享成功路径；
- ORGANIZATION 组织树共享若未来需要，须另接受 API-06 / DATA-06 切片，不得静默扩展。
