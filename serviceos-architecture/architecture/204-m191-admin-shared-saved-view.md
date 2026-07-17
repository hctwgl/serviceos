---
title: M191 Admin 共享 SavedView
status: Implemented
milestone: M191
lastUpdated: 2026-07-17
relatedMilestones: [M189, M190]
---

# M191 Admin 共享 SavedView

## 目标

在 M189 个人 SavedView 与 M190 UI Preferences 之上，为 Admin Portal 提供受控共享：
将本人视图查询定义共享给角色或租户，并在列表中合并可见共享视图；共享不等于数据授权。

## 范围与非目标

- 范围：
  - 窄接受 API-06 §8 共享切片与 DATA-06 §6.1（`PRIVATE`/`ROLE`/`TENANT`）；
  - ADR-029：`preference.shareSavedView` HIGH；owner 可收回 PRIVATE；
  - Core OpenAPI `0.83.0` `POST /saved-views/{id}:share`；列表返回可见共享；
  - Flyway `V093`：`visibility`、`shared_scope_ref`、索引与 capability 种子；
  - Admin Web SavedViewBar Share/Unshare、SHARED/PRIVATE 徽标；
  - PostgreSQL IT、MVC Security、ArchitectureTest、Admin E2E。
- 明确不做：
  - ORGANIZATION 组织树共享；
  - Network/Technician SavedView；
  - 共享 UI Preference、搜索、work-queues、Consumer Identity、评分。

## 事实源

- `api/06-application-query-preference-http-api.md` §0 / §8.1（共享切片 Accepted）
- `data/06-application-projection-preference-logical-model.md` §0 / §6.1
- ADR-029

## 设计要点

- 共享只改变查询定义可见性；应用视图时业务 API 仍重新鉴权；
- ROLE：`shared_scope_ref=roleId`，列表对持有有效 RoleGrant 的主体可见；
- TENANT：同租户主体可见；跨租户隔离；
- 取消共享：`share` body `visibility=PRIVATE`；
- share/unshare 写入业务审计。

## 已实现

- [x] ADR-029
- [x] OpenAPI Core `0.83.0`
- [x] Flyway `V093`
- [x] `SavedViewCommandService.share` / 列表合并 / `PrincipalActiveRoleQuery`
- [x] Admin Web Share/Unshare + 徽标
- [x] SharedSavedViewPostgresIT / Security MVC / ArchitectureTest / E2E

## 明确未实现

- ORGANIZATION 组织树共享；
- Network/Technician SavedView；
- 共享 UI Preference。

## 工程证据

- Flyway：`db/migration/readmodel/V093__alter_saved_view_share_visibility.sql`
- OpenAPI：`serviceos-core-v1.yaml` 0.83.0
- IT：`SharedSavedViewPostgresIT`、`SavedViewPostgresIT`
- MVC：`SavedViewControllerSecurityTest`
- E2E：`admin-shared-saved-views.spec.ts`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs contracts arch
./mvnw -pl serviceos-backend -am test \
  -Dtest=ArchitectureTest,SavedViewPostgresIT,SharedSavedViewPostgresIT,SavedViewControllerSecurityTest
bash scripts/verify-local.sh
```
