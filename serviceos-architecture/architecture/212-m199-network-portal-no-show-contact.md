---
title: M199 Network Portal 爽约与联系尝试
status: Implemented
milestone: M199
lastUpdated: 2026-07-17
relatedMilestones: [M30, M31, M122, M160, M197, M198]
---

# M199 Network Portal 爽约与联系尝试

## 目标

在 M197/M198 Network Portal 预约写切片之上，交付爽约判定与联系尝试适配器：按可信
`X-Network-Context` 对本网点任务记录 ContactAttempt，并对本网点已确认且窗口已结束的预约
执行 mark-no-show；失败关闭跨网点与伪造上下文。

## 范围与非目标

- 范围：
  - `POST /api/v1/network-portal/appointments/{appointmentId}:mark-no-show`；
  - `GET /api/v1/network-portal/tasks/{taskId}/contact-attempts`；
  - `POST /api/v1/network-portal/tasks/{taskId}/contact-attempts`；
  - ADR-037：适配器委托 `AppointmentService.markNoShow` / `listContactAttempts` /
    `recordContactAttempt`；
  - Core OpenAPI `0.91.0`；复用 Flyway V097 `networkPortal.manageAppointment`（无新迁移）；
  - Admin Web Network Portal 任务页爽约与联系尝试动作；
  - PostgreSQL IT、MVC Security、ArchitectureTest、Admin E2E spec。
- 明确不做：
  - 改派、资料补传 / submit-on-behalf、Visit、FieldOperation；
  - 离线工作包、ORGANIZATION SavedView、Consumer Identity；
  - 完整 product/03 Network 设计系统。

## 事实源

- ADR-037
- ADR-035 / ADR-036 Portal 门禁与能力种子
- M30/M31/M122/M160 Admin Appointment mark-no-show 与 ContactAttempt
- product/03：网点与客服/师傅共享 ContactAttempt / Appointment 历史

## 设计要点

- Body 与 Admin 同形；mark-no-show 的 `If-Match` 来自列表/回执 aggregateVersion；
- 鉴权顺序：解析上下文 → ACTIVE NetworkMembership → NETWORK
  `networkPortal.manageAppointment` → 任务/预约 ACTIVE NETWORK 责任=上下文网点 → 委托 Appointment；
  底层另需 `appointment.read`、`appointment.manage`（爽约）、`appointment.recordContact`（联系）；
- 爽约仅 CONFIRMED + 窗口已结束；联系事实不可变，操作者=JWT；
- 同幂等键重放成功；跨网点/伪造上下文失败关闭。

## 已实现

- [x] ADR-037
- [x] OpenAPI Core `0.91.0`
- [x] 复用 Flyway V097（无新迁移；097/99）
- [x] `NetworkPortalAppointmentService` markNoShow / listContactAttempts / recordContactAttempt
- [x] Admin Web 爽约/联系尝试 + E2E spec
- [x] PostgresIT / Security MVC / ArchitectureTest

## 明确未实现

- 改派 / 资料 Network 写；
- 完整 Network Portal 产品 UI / 设计系统；
- 离线工作包。

## 工程证据

- OpenAPI：`serviceos-core-v1.yaml` 0.91.0
- Flyway：V097（无 M199 新迁移）
- IT：`NetworkPortalNoShowContactPostgresIT`
- MVC：`NetworkPortalAppointmentControllerSecurityTest`
- E2E：`network-portal-no-show-contact.spec.ts`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs contracts arch
./mvnw -pl serviceos-backend -am test \
  -Dtest=ArchitectureTest,NetworkPortalAppointmentPostgresIT,NetworkPortalAppointmentLifecyclePostgresIT,NetworkPortalNoShowContactPostgresIT,NetworkPortalAppointmentControllerSecurityTest,AppointmentPostgresIT
./mvnw -pl serviceos-contracts -am test
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
