---
title: M189 Admin 个人 SavedView 验收矩阵
status: Implemented
milestone: M189
lastUpdated: 2026-07-17
---

# M189 Admin 个人 SavedView 验收矩阵

| 编号 | 场景 | 预期 | 结果 |
|---|---|---|---|
| M189-01 | 已认证主体对自己 pageId 创建/列表/更新/删除 SavedView | CRUD 成功；默认视图同页唯一 | pass（PostgresIT） |
| M189-02 | 跨主体或跨租户更新/删除 | `RESOURCE_NOT_FOUND`，不泄露存在性 | pass（PostgresIT + Security） |
| M189-03 | 未知筛选字段或非 EQ 操作符 | `QUERY_FILTER_NOT_ALLOWED`（422） | pass（PostgresIT） |
| M189-04 | 存储 schemaVersion 与当前目录不兼容时更新 | `SAVED_VIEW_SCHEMA_OUTDATED`（409） | pass（PostgresIT） |
| M189-05 | 未认证访问 `/me/saved-views` | 401 | pass（Security） |
| M189-06 | Admin Web：任务目录保存筛选 → 重载应用 → 删除 | 筛选恢复；删除后 picker 无该项 | pass（E2E，需 Keycloak 栈） |
| M189-07 | 应用视图不绕过页面 capability | 无页面能力时业务列表仍 403；视图行可残留 | pass（设计/ADR-027；业务 API 既有门禁） |
| M189-08 | 契约与迁移门禁 | Core OpenAPI 0.81.0、Flyway V091/93、ArchitectureTest | pass（contracts + arch） |

## 证据

- `SavedViewPostgresIT`
- `SavedViewControllerSecurityTest`
- `ArchitectureTest`
- `serviceos-admin-web/tests/e2e/admin-saved-views.spec.ts`
- Core OpenAPI `0.81.0`
- Flyway `V091`
