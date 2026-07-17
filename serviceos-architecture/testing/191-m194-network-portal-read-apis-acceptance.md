---
title: M194 Network Portal 只读查询验收矩阵
status: Implemented
milestone: M194
lastUpdated: 2026-07-17
---

# M194 Network Portal 只读查询验收矩阵

| 编号 | 场景 | 预期 | 结果 |
|---|---|---|---|
| M194-01 | ACTIVE NetworkMembership + `networkTask.read` 列出本网点 ACTIVE assignment 工单/任务 | 仅见本网点 ACTIVE 责任；含 workbench 计数 | pass（NetworkPortalReadPostgresIT） |
| M194-02 | 非成员或伪造 `X-Network-Context` | 403 `PORTAL_CONTEXT_INVALID` | pass（PostgresIT + Security） |
| M194-03 | 成员 A 请求网点 B（无 membership） | 403 `PORTAL_CONTEXT_INVALID`；不泄露 B 数据 | pass（NetworkPortalReadPostgresIT） |
| M194-04 | 本网点 ACTIVE NetworkTechnicianMembership 列表 | 仅 ACTIVE；终止关系不可见 | pass（NetworkPortalReadPostgresIT） |
| M194-05 | capacity 按 networkId 读 `dsp_capacity_counter` | 返回本网点计数；跨网点隔离 | pass（NetworkPortalReadPostgresIT） |
| M194-06 | 未认证访问 network-portal | 401 | pass（Security） |
| M194-07 | 缺 `networkTask.read` / `technician.readOwnNetwork`（已是成员） | 403 `ACCESS_DENIED` | pass（NetworkPortalReadPostgresIT） |
| M194-08 | Admin Web：选 NETWORK context → 列表页带 `X-Network-Context`；伪造上下文失败关闭 | UI 与 API 一致 | pass（E2E spec，需 Keycloak + NETWORK 人格时） |
| M194-09 | 契约与模块边界 | Core OpenAPI 0.86.0、Flyway 095/97、ArchitectureTest | pass（contracts + arch） |

## 证据

- `NetworkPortalReadPostgresIT`
- `NetworkPortalControllerSecurityTest`
- `ArchitectureTest`
- `serviceos-admin-web/tests/e2e/network-portal-read.spec.ts`
- Core OpenAPI `0.86.0`
