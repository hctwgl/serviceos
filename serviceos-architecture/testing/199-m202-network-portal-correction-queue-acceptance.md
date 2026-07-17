---
title: M202 Network Portal 整改队列只读验收矩阵
status: Implemented
milestone: M202
lastUpdated: 2026-07-17
---

# M202 Network Portal 整改队列只读验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M202-01 | ACTIVE 成员 + `evidence.read` NETWORK + 本网点 OPEN 整改 | list 返回该案例 | pass（NetworkPortalCorrectionQueuePostgresIT） |
| M202-02 | 他网点 ACTIVE 任务上的整改 | list 不返回 | pass（NetworkPortalCorrectionQueuePostgresIT） |
| M202-03 | get 本网点案例 | 200 详情 | pass（NetworkPortalCorrectionQueuePostgresIT） |
| M202-04 | get 跨网点案例 | ACCESS_DENIED | pass（NetworkPortalCorrectionQueuePostgresIT） |
| M202-05 | 伪造 `X-Network-Context` | 403 `PORTAL_CONTEXT_INVALID` | pass（NetworkPortalCorrectionQueuePostgresIT / E2E） |
| M202-06 | 未认证 HTTP | 401 | pass（NetworkPortalControllerSecurityTest） |
| M202-07 | 成员但缺 `evidence.read` | 403 `ACCESS_DENIED` | pass（NetworkPortalCorrectionQueuePostgresIT） |
| M202-08 | Admin Web `/network-portal/corrections` 列表 | UI 可见；伪造上下文拒绝 | pass（network-portal-correction-queue.spec.ts） |
| M202-09 | OpenAPI Core `0.94.0`；Flyway 仍 099/101 | 契约与迁移门禁通过 | pass（contracts / preflight） |
| M202-10 | ArchitectureTest 模块边界 | readmodel 经 api fan-in | pass（ArchitectureTest） |
