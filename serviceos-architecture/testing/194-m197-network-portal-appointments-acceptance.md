---
title: M197 Network Portal 预约协作验收矩阵
status: Implemented
milestone: M197
lastUpdated: 2026-07-17
---

# M197 Network Portal 预约协作验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M197-01 | ACTIVE NetworkMembership + `networkPortal.manageAppointment` + 本网点任务 propose+confirm | PROPOSED→CONFIRMED；confirmedPartyType=NETWORK_MEMBER | pass（NetworkPortalAppointmentPostgresIT） |
| M197-02 | 任务 ACTIVE NETWORK 属于其它网点 | 403 `ACCESS_DENIED` | pass（NetworkPortalAppointmentPostgresIT） |
| M197-03 | 主体对上下文网点无 ACTIVE membership | 403 `PORTAL_CONTEXT_INVALID` | pass（NetworkPortalAppointmentPostgresIT） |
| M197-04 | 伪造 `X-Network-Context` | 403 `PORTAL_CONTEXT_INVALID` | pass（NetworkPortalAppointmentPostgresIT + E2E） |
| M197-05 | confirm 使用 TECHNICIAN confirmedPartyType | 400/业务校验失败关闭 | pass（NetworkPortalAppointmentPostgresIT） |
| M197-06 | 未认证 | 401 | pass（NetworkPortalAppointmentControllerSecurityTest） |
| M197-07 | 成员但缺 `networkPortal.manageAppointment` | 403 `ACCESS_DENIED` | pass（NetworkPortalAppointmentControllerSecurityTest） |
| M197-08 | 契约与模块边界 | Core OpenAPI 0.89.0、Flyway 097/99、ArchitectureTest | pass（contracts + arch） |
| M197-09 | Admin Web 预约表单携带 `X-Network-Context` | 伪造上下文拒绝；有上下文时展示 propose/confirm | pass（network-portal-appointments.spec.ts） |

## 工程证据入口

- Core OpenAPI `0.89.0`
- Flyway V097
- `NetworkPortalAppointmentPostgresIT`
- `NetworkPortalAppointmentControllerSecurityTest`
- `ArchitectureTest`
- `network-portal-appointments.spec.ts`
