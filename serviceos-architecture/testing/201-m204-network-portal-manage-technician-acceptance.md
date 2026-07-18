---
title: M204 Network Portal 师傅关系与资质提交验收矩阵
status: Implemented
milestone: M204
lastUpdated: 2026-07-17
---

# M204 Network Portal 师傅关系与资质提交验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M204-01 | ACTIVE 成员 + `networkPortal.manageTechnician` → create membership | ACTIVE 关系；networkId=上下文 | pass（NetworkPortalManageTechnicianPostgresIT） |
| M204-02 | terminate 本网点 membership | TERMINATED | pass（NetworkPortalManageTechnicianPostgresIT） |
| M204-03 | submit qualification（ACTIVE 师傅） | PENDING | pass（NetworkPortalManageTechnicianPostgresIT） |
| M204-04 | submit 非本网点师傅 | VALIDATION_FAILED | pass（NetworkPortalManageTechnicianPostgresIT） |
| M204-05 | terminate 他网点 membership | ACCESS_DENIED | pass（NetworkPortalManageTechnicianPostgresIT） |
| M204-06 | 伪造 `X-Network-Context` | 403 PORTAL_CONTEXT_INVALID | pass（IT / E2E） |
| M204-07 | 未认证 HTTP | 401 | pass（NetworkPortalManageTechnicianControllerSecurityTest） |
| M204-08 | 缺 `networkPortal.manageTechnician` | 403 ACCESS_DENIED | pass（IT / Security） |
| M204-09 | Admin TENANT `network.manageTechnician` 路径不变 | 仍可用 | pass（既有 Network*PostgresIT 回归） |
| M204-10 | Admin Web 控件 | UI 可见；伪造拒绝 | pass（network-portal-manage-technician.spec.ts） |
| M204-11 | OpenAPI 0.96.0；Flyway 100/102 | 契约与迁移门禁 | pass（contracts / preflight） |
| M204-12 | ArchitectureTest | network 边界保持 | pass（ArchitectureTest） |
