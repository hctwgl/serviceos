---
title: M192 Admin 受控全局搜索验收矩阵
status: Implemented
milestone: M192
lastUpdated: 2026-07-17
---

# M192 Admin 受控全局搜索验收矩阵

| 编号 | 场景 | 预期 | 结果 |
|---|---|---|---|
| M192-01 | 授权主体搜索已知工单 UUID / externalOrderCode | 返回 WORK_ORDER（及 EXTERNAL_ORDER 同码）命中与 deepLink | pass（ControlledSearchPostgresIT） |
| M192-02 | 授权主体搜索网点 code/name 前缀与师傅 displayName/工号 | NETWORK / TECHNICIAN 命中 | pass（ControlledSearchPostgresIT） |
| M192-03 | 请求 VEHICLE/CHARGER 等未支持 type | `SEARCH_TERM_NOT_ALLOWED` 422 | pass（ControlledSearchPostgresIT） |
| M192-04 | 跨租户资源 | 结果为空 / 不可见 | pass（ControlledSearchPostgresIT） |
| M192-05 | 缺 `search.read` | `ACCESS_DENIED` 403 | pass（PostgresIT + Security） |
| M192-06 | 有 `search.read` 缺 type 读能力 | 省略该 type，其余 type 仍可返回 | pass（ControlledSearchPostgresIT） |
| M192-07 | 完整手机号形态 `q` | `SEARCH_TERM_NOT_ALLOWED`；响应不含完整原文 | pass（ControlledSearchPostgresIT） |
| M192-08 | 未认证访问 `/search` | 401 | pass（Security） |
| M192-09 | Admin Web：登录 → 搜索已知工单/网点 → 结果深链 | UI 与 API 一致 | pass（E2E，需 Keycloak 栈） |
| M192-10 | 契约与模块边界 | Core OpenAPI 0.84.0、Flyway V094/96、ArchitectureTest | pass（contracts + arch） |

## 证据

- `ControlledSearchPostgresIT`
- `ControlledSearchControllerSecurityTest`
- `ArchitectureTest`
- `serviceos-admin-web/tests/e2e/admin-controlled-search.spec.ts`
- Core OpenAPI `0.84.0`
- Flyway `V094`
