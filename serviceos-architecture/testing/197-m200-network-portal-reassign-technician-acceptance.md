---
title: M200 Network Portal 改派师傅验收矩阵
status: Implemented
milestone: M200
lastUpdated: 2026-07-17
---

# M200 Network Portal 改派师傅验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M200-01 | ACTIVE NetworkMembership + `networkPortal.reassignTechnician` + 本网点 ACTIVE NETWORK + 不同 ACTIVE TECHNICIAN → 目标师傅 | 旧 TECHNICIAN ENDED、新 ACTIVE；NETWORK 不变；幂等重放一致 | pass（NetworkPortalReassignTechnicianPostgresIT） |
| M200-02 | 无 ACTIVE TECHNICIAN 时改派 | `VALIDATION_FAILED`（应走 assign） | pass（NetworkPortalReassignTechnicianPostgresIT） |
| M200-03 | 目标师傅不在本网点 | `VALIDATION_FAILED` | pass（NetworkPortalReassignTechnicianPostgresIT） |
| M200-04 | 跨网点 ACTIVE NETWORK | `SERVICE_ASSIGNMENT_CONFLICT` / ACCESS_DENIED | pass（NetworkPortalReassignTechnicianPostgresIT） |
| M200-05 | 伪造 `X-Network-Context` | 403 `PORTAL_CONTEXT_INVALID` | pass（NetworkPortalReassignTechnicianPostgresIT / E2E） |
| M200-06 | 未认证 HTTP | 401 | pass（NetworkPortalReassignTechnicianControllerSecurityTest） |
| M200-07 | 成员但缺 `networkPortal.reassignTechnician` | 403 `ACCESS_DENIED` | pass（NetworkPortalReassignTechnicianControllerSecurityTest / PostgresIT） |
| M200-08 | Admin Web 展示改派控件；伪造上下文失败关闭 | UI 可见（有 NETWORK 上下文时）；伪造拒绝文案 | pass（network-portal-reassign-technician.spec.ts） |
| M200-09 | OpenAPI Core `0.92.0`；Flyway 098/100 | 契约与迁移门禁通过 | pass（contracts / preflight） |
| M200-10 | ArchitectureTest 模块边界 | dispatch 仅经 api/spi | pass（ArchitectureTest） |
