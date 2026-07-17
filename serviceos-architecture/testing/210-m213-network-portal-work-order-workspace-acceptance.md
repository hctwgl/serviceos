---
title: M213 Network Portal 限定工单工作区验收矩阵
status: Implemented
milestone: M213
lastUpdated: 2026-07-17
---

# M213 Network Portal 限定工单工作区验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M213-01 | ACTIVE NETWORK 责任工单 GET workspace | 200 + 头字段 + tasks | pass（NetworkPortalReadPostgresIT） |
| M213-02 | 他网点 / 无 ACTIVE 责任工单 | ACCESS_DENIED | pass（NetworkPortalReadPostgresIT） |
| M213-03 | 伪造 NETWORK 上下文 | PORTAL_CONTEXT_INVALID | pass（既有门户门禁 + SecurityTest） |
| M213-04 | 未认证 | 401 | pass（NetworkPortalControllerSecurityTest） |
| M213-05 | Page Registry 含 `NETWORK.WORKORDER.WORKSPACE` | catalog v16 | pass（PortalContextPostgresIT） |
| M213-06 | Admin Web 列表深链详情 | 详情壳 + 任务表 | pass（network-portal-work-order-workspace.spec.ts） |
| M213-07 | OpenAPI 1.0.0；Flyway 仍 100/102 | 契约升版无迁移 | pass（contracts / preflight） |
| M213-08 | ArchitectureTest | 模块边界 | pass（ArchitectureTest） |
