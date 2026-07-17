---
title: M199 Network Portal 爽约与联系尝试验收矩阵
status: Implemented
milestone: M199
lastUpdated: 2026-07-17
---

# M199 Network Portal 爽约与联系尝试验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M199-01 | ACTIVE NetworkMembership + `networkPortal.manageAppointment` + 本网点已确认且窗口已结束预约 mark-no-show | 状态 `NO_SHOW`；幂等重放一致 | pass（NetworkPortalNoShowContactPostgresIT） |
| M199-02 | 本网点任务 recordContactAttempt + list | 201/列表含新事实；actor=JWT | pass（NetworkPortalNoShowContactPostgresIT） |
| M199-03 | 窗口未结束的 CONFIRMED 预约 mark-no-show | `APPOINTMENT_WINDOW_NOT_ENDED` | pass（NetworkPortalNoShowContactPostgresIT） |
| M199-04 | 跨网点预约/任务 mark-no-show 或 recordContact | 403 `ACCESS_DENIED` | pass（NetworkPortalNoShowContactPostgresIT） |
| M199-05 | 伪造 `X-Network-Context` | 403 `PORTAL_CONTEXT_INVALID` | pass（NetworkPortalNoShowContactPostgresIT / E2E） |
| M199-06 | 未认证 HTTP | 401 | pass（NetworkPortalAppointmentControllerSecurityTest） |
| M199-07 | 成员但缺 `networkPortal.manageAppointment`（服务层抛 ACCESS_DENIED） | 403 `ACCESS_DENIED` | pass（NetworkPortalAppointmentControllerSecurityTest） |
| M199-08 | Admin Web Network Portal 展示爽约/联系控件；伪造上下文失败关闭 | UI 控件可见（有 NETWORK 上下文时）；伪造拒绝文案 | pass（network-portal-no-show-contact.spec.ts） |
| M199-09 | OpenAPI Core `0.91.0`；Flyway 仍为 097/99 | 契约与迁移门禁通过 | pass（contracts / preflight） |
| M199-10 | ArchitectureTest 模块边界 | appointment 仅经 api/spi | pass（ArchitectureTest） |
