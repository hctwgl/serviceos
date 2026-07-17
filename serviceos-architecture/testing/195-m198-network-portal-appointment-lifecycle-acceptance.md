---
title: M198 Network Portal 预约生命周期（改约/取消）验收矩阵
status: Implemented
milestone: M198
lastUpdated: 2026-07-17
---

# M198 Network Portal 预约生命周期（改约/取消）验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M198-01 | ACTIVE NetworkMembership + `networkPortal.manageAppointment` + 本网点预约 confirm→reschedule→cancel | CONFIRMED→PROPOSED→CANCELLED；幂等重放一致 | pass（NetworkPortalAppointmentLifecyclePostgresIT） |
| M198-02 | 预约属于其它网点 ACTIVE NETWORK | 403 `ACCESS_DENIED` | pass（NetworkPortalAppointmentLifecyclePostgresIT） |
| M198-03 | 伪造 `X-Network-Context` | 403 `PORTAL_CONTEXT_INVALID` | pass（NetworkPortalAppointmentLifecyclePostgresIT + E2E） |
| M198-04 | 过期 If-Match 改约 | 409 `APPOINTMENT_VERSION_CONFLICT` | pass（NetworkPortalAppointmentLifecyclePostgresIT） |
| M198-05 | 未认证 reschedule/cancel | 401 | pass（NetworkPortalAppointmentControllerSecurityTest） |
| M198-06 | 成员但缺 `networkPortal.manageAppointment` | 403 `ACCESS_DENIED` | pass（NetworkPortalAppointmentControllerSecurityTest） |
| M198-07 | 契约与模块边界 | Core OpenAPI 0.90.0、Flyway 097/99、ArchitectureTest | pass（contracts + arch） |
| M198-08 | Admin Web 展示改约/取消控件；伪造上下文拒绝 | 表单可见；伪造拒绝文案 | pass（network-portal-appointment-lifecycle.spec.ts） |

## 工程证据入口

- Core OpenAPI `0.90.0`
- Flyway V097（无新迁移）
- `NetworkPortalAppointmentLifecyclePostgresIT`
- `NetworkPortalAppointmentControllerSecurityTest`
- `ArchitectureTest`
- `network-portal-appointment-lifecycle.spec.ts`
