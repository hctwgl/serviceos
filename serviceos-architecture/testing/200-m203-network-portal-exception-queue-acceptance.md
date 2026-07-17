---
title: M203 Network Portal 运营异常队列只读验收矩阵
status: Implemented
milestone: M203
lastUpdated: 2026-07-17
---

# M203 Network Portal 运营异常队列只读验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M203-01 | ACTIVE 成员 + `operations.exception.read` NETWORK + 本网点 OPEN 异常 | list 返回该异常；allowedActions 为空 | pass（NetworkPortalExceptionQueuePostgresIT） |
| M203-02 | 他网点任务上的异常 | list 不返回 | pass（NetworkPortalExceptionQueuePostgresIT） |
| M203-03 | get 本网点异常 | 200 详情 | pass（NetworkPortalExceptionQueuePostgresIT） |
| M203-04 | get 跨网点异常 | ACCESS_DENIED | pass（NetworkPortalExceptionQueuePostgresIT） |
| M203-05 | 伪造 `X-Network-Context` | 403 `PORTAL_CONTEXT_INVALID` | pass（NetworkPortalExceptionQueuePostgresIT / E2E） |
| M203-06 | 未认证 HTTP | 401 | pass（NetworkPortalControllerSecurityTest） |
| M203-07 | 成员但缺 `operations.exception.read` | 403 `ACCESS_DENIED` | pass（NetworkPortalExceptionQueuePostgresIT） |
| M203-08 | Admin Web `/network-portal/exceptions` | UI 可见；深链任务；伪造拒绝 | pass（network-portal-exception-queue.spec.ts） |
| M203-09 | OpenAPI Core `0.95.0`；Flyway 仍 099/101 | 契约与迁移门禁通过 | pass（contracts / preflight） |
| M203-10 | ArchitectureTest | operations→dispatch::api；readmodel fan-in | pass（ArchitectureTest） |
