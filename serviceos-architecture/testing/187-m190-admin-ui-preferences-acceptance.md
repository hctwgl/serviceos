---
title: M190 Admin UI Preferences 验收矩阵
status: Implemented
milestone: M190
lastUpdated: 2026-07-17
---

# M190 Admin UI Preferences 验收矩阵

| 编号 | 场景 | 预期 | 结果 |
|---|---|---|---|
| M190-01 | 已认证主体 GET/PUT/DELETE 自己的 Admin UI 偏好 | CRUD 成功；DELETE 后键消失 | pass（PostgresIT） |
| M190-02 | 跨主体或跨租户读取/写入 | 空文档或不影响他人行；写他人按主体作用域隔离 | pass（PostgresIT） |
| M190-03 | 未知键或禁止键（如 disableSecurityConfirmations） | `UI_PREFERENCE_KEY_NOT_ALLOWED`（422） | pass（PostgresIT） |
| M190-04 | portal≠ADMIN | `VALIDATION_FAILED` | pass（PostgresIT） |
| M190-05 | expectedVersion 冲突 | `VERSION_CONFLICT` | pass（PostgresIT） |
| M190-06 | 未认证访问 `/me/ui-preferences` | 401 | pass（Security） |
| M190-07 | Admin Web：设置 theme → 重载仍持久 | CSS 类与 API 值一致 | pass（E2E，需 Keycloak 栈） |
| M190-08 | 契约与迁移门禁 | Core OpenAPI 0.82.0、Flyway V092/94、ArchitectureTest | pass（contracts + arch） |

## 证据

- `UiPreferencePostgresIT`
- `UiPreferenceControllerSecurityTest`
- `ArchitectureTest`
- `serviceos-admin-web/tests/e2e/admin-ui-preferences.spec.ts`
- Core OpenAPI `0.82.0`
- Flyway `V092`
