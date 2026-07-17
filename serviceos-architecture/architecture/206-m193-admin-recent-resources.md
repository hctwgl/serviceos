---
title: M193 Admin 最近访问
status: Implemented
milestone: M193
lastUpdated: 2026-07-17
relatedMilestones: [M188, M189, M190, M191, M192]
---

# M193 Admin 最近访问

## 目标

在 Admin 偏好/搜索轨道之上，为 Admin Portal 提供个人最近访问列表：touch 记录访问，
GET 时按当前授权过滤并深链回资源详情，不扩大 Capability 真相。

## 范围与非目标

- 范围：
  - 窄接受 API-06 §3 Admin 最近访问与 DATA-06 §8 个人 Admin RecentResource；
  - ADR-031：归属 `readmodel`；已认证主体读写自己的最近列表；读时重鉴权；
  - Core OpenAPI `0.85.0` `GET/PUT /me/recent-resources`；
  - Flyway `V095` `rdm_recent_resource`；
  - 类型：`WORK_ORDER`、`TASK`、`PROJECT`、`NETWORK`、`TECHNICIAN`；
  - Admin Web：详情页 touch + AppShell 最近访问条；
  - PostgreSQL IT、MVC Security、ArchitectureTest、Admin E2E。
- 明确不做：
  - `GET /me/notifications`、`GET /me/application-context`；
  - Network/Technician Portal 最近访问；
  - ORGANIZATION SavedView、`search_document`、Consumer Identity、workbench/work-queues。

## 事实源

- `api/06-application-query-preference-http-api.md` §0 / §3（M193 Accepted）
- `data/06-application-projection-preference-logical-model.md` §0 / §8.1
- ADR-031

## 设计要点

- tenant + principal 作用域；portal 固定 `ADMIN`；
- unique `(tenant, principal, portal, resource_type, resource_id)` upsert `last_visited_at`；
- GET 经既有授权端口重鉴权；失权省略并读路径删除；上限 20；
- `displayRef` 失败关闭拒绝敏感形态；不授予数据能力。

## 已实现

- [x] ADR-031
- [x] OpenAPI Core `0.85.0`
- [x] Flyway `V095`
- [x] `RecentResourceQueryService` / `RecentResourceCommandService` / Controller
- [x] Admin Web touch + AppShell Recent
- [x] PostgresIT / Security MVC / ArchitectureTest / E2E

## 明确未实现

- 通知 / application-context；
- Network/Technician Portal 最近访问；
- REVIEW_CASE / CORRECTION_CASE 类型（可选后续切片）。

## 工程证据

- Flyway：`db/migration/readmodel/V095__create_admin_recent_resource.sql`
- OpenAPI：`serviceos-core-v1.yaml` 0.85.0
- IT：`RecentResourcePostgresIT`
- MVC：`RecentResourceControllerSecurityTest`
- E2E：`admin-recent-resources.spec.ts`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs contracts arch
./mvnw -pl serviceos-backend -am test \
  -Dtest=ArchitectureTest,RecentResourcePostgresIT,RecentResourceControllerSecurityTest
bash scripts/verify-local.sh
```
