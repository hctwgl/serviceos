---
title: M196 Network Portal 指派师傅验收矩阵
status: Implemented
milestone: M196
lastUpdated: 2026-07-17
---

# M196 Network Portal 指派师傅验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M196-01 | ACTIVE NetworkMembership + `networkPortal.assignTechnician` + ACTIVE 师傅关系指派 | 双 ACTIVE NETWORK+TECHNICIAN；networkAssigneeId=上下文网点 | pass（NetworkPortalAssignTechnicianPostgresIT） |
| M196-02 | 主体对上下文网点无 ACTIVE membership | 403 `PORTAL_CONTEXT_INVALID` | pass（NetworkPortalAssignTechnicianPostgresIT） |
| M196-03 | 师傅对本网点无 ACTIVE NetworkTechnicianMembership | 拒绝（失败关闭） | pass（NetworkPortalAssignTechnicianPostgresIT） |
| M196-04 | 任务已有不同网点 ACTIVE NETWORK | 冲突失败关闭 | pass（NetworkPortalAssignTechnicianPostgresIT） |
| M196-05 | 任务已有不同师傅 ACTIVE TECHNICIAN | `SERVICE_ASSIGNMENT_CONFLICT` | pass（NetworkPortalAssignTechnicianPostgresIT） |
| M196-06 | 伪造 `X-Network-Context` | 403 `PORTAL_CONTEXT_INVALID` | pass（NetworkPortalAssignTechnicianPostgresIT + E2E） |
| M196-07 | 未认证 | 401 | pass（NetworkPortalAssignTechnicianControllerSecurityTest） |
| M196-08 | 成员但缺 `networkPortal.assignTechnician` | 403 `ACCESS_DENIED` | pass（NetworkPortalAssignTechnicianControllerSecurityTest / PostgresIT） |
| M196-09 | 契约与模块边界 | Core OpenAPI 0.88.0、Flyway 096/98、ArchitectureTest | pass（contracts + arch） |
| M196-10 | Admin Web 指派表单携带 `X-Network-Context` | 伪造上下文拒绝；有上下文时可提交指派 | pass（network-portal-assign-technician.spec.ts） |

## 工程证据入口

- Core OpenAPI `0.88.0`
- Flyway V096
- `NetworkPortalAssignTechnicianPostgresIT`
- `NetworkPortalAssignTechnicianControllerSecurityTest`
- `ArchitectureTest`
- `network-portal-assign-technician.spec.ts`
