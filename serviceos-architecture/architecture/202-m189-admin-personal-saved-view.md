---
title: M189 Admin 个人 SavedView
status: Implemented
milestone: M189
lastUpdated: 2026-07-17
relatedMilestones: [M188]
---

# M189 Admin 个人 SavedView

## 目标

在 M188 Portal 上下文与导航之上，为 Admin Portal 已有 Accepted 筛选目录的页面提供**个人**
SavedView CRUD：保存受控筛选 AST、在客户端应用恢复筛选，且不改变页面/数据授权真相。

## 范围与非目标

- 范围：
  - 窄接受 API-06 §8 个人 CRUD 与 DATA-06 §6 个人 `saved_view` 切片；
  - ADR-027：归属 `readmodel`；已认证主体 CRUD 自己的视图；
  - Core OpenAPI `0.81.0` `/me/saved-views*`；
  - Flyway `V091` `rdm_saved_view`；
  - pageId：`ADMIN.TASK.QUEUE`、`ADMIN.WORKORDER.LIST`、`ADMIN.CORRECTION.QUEUE`；
  - Admin Web SavedView 条（任务/工单目录至少；整改队列同步接入）；
  - PostgreSQL IT、MVC Security、ArchitectureTest、Admin E2E。
- 明确不做：
  - `POST /saved-views/{id}:share`、角色/组织共享；
  - UI Preference（API-06 §9）；
  - 通用 work-queues；
  - Network/Technician SavedView；
  - Consumer Identity、评分、BUSINESS SLA、MFA executor。

## 事实源

- `api/06-application-query-preference-http-api.md` §0 / §8（个人切片 Accepted）
- `data/06-application-projection-preference-logical-model.md` §0 / §6（个人切片 Accepted）
- ADR-027

## 设计要点

- tenant + principal 作用域；跨主体更新/删除按 `RESOURCE_NOT_FOUND`；
- Filter 目录与 schemaVersion 代码权威；未知字段 `QUERY_FILTER_NOT_ALLOWED`（422）；
  目录不兼容 `SAVED_VIEW_SCHEMA_OUTDATED`（409）；
- 列表/应用视图不授予 capability；页面业务 API 仍重新鉴权；
- 无分享表；portal 固定 `ADMIN`。

## 已实现

- [x] ADR-027
- [x] OpenAPI Core `0.81.0`
- [x] Flyway `V091`
- [x] `SavedViewQueryService` / `SavedViewCommandService` / `SavedViewController`
- [x] Admin Web SavedViewBar + Task/WorkOrder/Correction 页面
- [x] PostgresIT / Security MVC / ArchitectureTest / E2E

## 明确未实现

- 共享 SavedView / share API；
- UI Preference；
- Network/Technician 偏好；
- 设计系统级筛选组件重构。

## 工程证据

- Flyway：`db/migration/readmodel/V091__create_admin_personal_saved_view.sql`
- OpenAPI：`serviceos-core-v1.yaml` 0.81.0
- IT：`SavedViewPostgresIT`
- MVC：`SavedViewControllerSecurityTest`
- E2E：`admin-saved-views.spec.ts`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs
bash scripts/agent-verify.sh contracts
bash scripts/agent-verify.sh arch
./mvnw -pl serviceos-backend -am test \
  -Dtest=ArchitectureTest,SavedViewPostgresIT,SavedViewControllerSecurityTest
bash scripts/verify-local.sh
```
