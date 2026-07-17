---
title: M193 Admin 最近访问验收矩阵
status: Implemented
milestone: M193
lastUpdated: 2026-07-17
---

# M193 Admin 最近访问验收矩阵

| 编号 | 场景 | 预期 | 结果 |
|---|---|---|---|
| M193-01 | 已认证主体 touch 同一资源两次 | upsert；`lastVisitedAt` 更新；唯一键不重复 | pass（RecentResourcePostgresIT） |
| M193-02 | 访问两资源后再次 touch 较旧项 | GET 按 `lastVisitedAt` 倒序，最近项在前 | pass（RecentResourcePostgresIT） |
| M193-03 | 列表含当前不可访问资源行 | 该项省略；整列表不 403；可读路径删除幽灵行 | pass（RecentResourcePostgresIT） |
| M193-04 | 跨主体 / 跨租户 | 互不可见 | pass（RecentResourcePostgresIT） |
| M193-05 | touch 超过上限（21） | 存储裁剪为 20 | pass（RecentResourcePostgresIT） |
| M193-06 | displayRef 含完整电话 / 非 ADMIN portal | `VALIDATION_FAILED` | pass（RecentResourcePostgresIT） |
| M193-07 | 未认证访问 GET/PUT | 401 | pass（Security） |
| M193-08 | 控制器仅使用 JWT 派生主体 | 无目标 principal 参数；跨主体路径不可达 | pass（Security） |
| M193-09 | Admin Web：打开工单详情 → 侧栏最近出现 → 深链 | UI 与 API 一致 | pass（E2E，需 Keycloak 栈） |
| M193-10 | 契约与模块边界 | Core OpenAPI 0.85.0、Flyway V095/97、ArchitectureTest | pass（contracts + arch） |

## 证据

- `RecentResourcePostgresIT`
- `RecentResourceControllerSecurityTest`
- `ArchitectureTest`
- `serviceos-admin-web/tests/e2e/admin-recent-resources.spec.ts`
- Core OpenAPI `0.85.0`
- Flyway `V095`
