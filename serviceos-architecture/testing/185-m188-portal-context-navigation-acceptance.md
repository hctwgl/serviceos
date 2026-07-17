---
title: M188 Portal 上下文与导航验收矩阵
status: Implemented
milestone: M188
lastUpdated: 2026-07-17
---

# M188 Portal 上下文与导航验收矩阵

| 编号 | 场景 | 预期 | 结果 |
|---|---|---|---|
| M188-01 | 多 Persona 用户调用 `/me/contexts` | 返回有效 ADMIN/NETWORK/TECHNICIAN contexts、范围摘要与版本；不含 CONSUMER Portal | pass（PostgresIT） |
| M188-02 | 请求未返回的 network/project context | 失败关闭，不能通过请求体/本地存储扩权 | pass（PostgresIT + Security + E2E stub） |
| M188-03 | `/me/navigation` | 由 Page Registry、Capability、feature gate 与 context 计算；pageId 稳定 | pass（PostgresIT + E2E） |
| M188-04 | 直接访问隐藏 URL 或伪造 allowed action | 后端数据和命令仍重新鉴权；导航不成为授权事实 | pass（E2E + Security） |
| M188-05 | 授权/成员关系变化后刷新 | context/navigation 版本变化；旧 expectedContextVersion 409 | pass（PostgresIT + E2E viewer） |
| M188-06 | Consumer Persona 兼容性 | Schema/目录预留 CONSUMER；不暴露 C 端入口 | pass（CONTRACT/PostgresIT） |

## 证据

- `PortalContextPostgresIT`
- `PortalContextControllerSecurityTest`
- `ArchitectureTest`
- `serviceos-admin-web/tests/e2e/admin-portal-context.spec.ts`
- Core OpenAPI `0.80.0`
- Flyway `V090`
