---
title: M191 Admin 共享 SavedView 验收矩阵
status: Implemented
milestone: M191
lastUpdated: 2026-07-17
---

# M191 Admin 共享 SavedView 验收矩阵

| 编号 | 场景 | 预期 | 结果 |
|---|---|---|---|
| M191-01 | owner 具备 `preference.shareSavedView` 将视图共享为 TENANT | visibility=TENANT；同租户其他主体列表可见 | pass（SharedSavedViewPostgresIT） |
| M191-02 | 共享为 ROLE 且 viewer 持有该 RoleGrant | viewer 列表可见；无该角色主体不可见 | pass（SharedSavedViewPostgresIT） |
| M191-03 | 无 share capability 分享 ROLE/TENANT | `ACCESS_DENIED`；行仍为 PRIVATE | pass（PostgresIT + Security） |
| M191-04 | owner 将 visibility 设为 PRIVATE | 他人列表不再可见；失权后仍可收回 | pass（SharedSavedViewPostgresIT） |
| M191-05 | 跨主体 share / 跨租户列表 | 404 / 不可见 | pass（SharedSavedViewPostgresIT） |
| M191-06 | 未认证访问 `:share` | 401 | pass（Security） |
| M191-07 | Admin Web：创建 → 共享 TENANT → 徽标 → 取消共享 | UI 与 API 一致（需本地授予能力） | pass（E2E，需 Keycloak 栈） |
| M191-08 | 契约与迁移门禁 | Core OpenAPI 0.83.0、Flyway V093/95、ArchitectureTest | pass（contracts + arch） |

## 证据

- `SharedSavedViewPostgresIT`
- `SavedViewPostgresIT`
- `SavedViewControllerSecurityTest`
- `ArchitectureTest`
- `serviceos-admin-web/tests/e2e/admin-shared-saved-views.spec.ts`
- Core OpenAPI `0.83.0`
- Flyway `V093`
