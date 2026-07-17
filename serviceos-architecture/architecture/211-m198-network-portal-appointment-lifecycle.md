---
title: M198 Network Portal 预约生命周期（改约/取消）
status: Implemented
milestone: M198
lastUpdated: 2026-07-17
relatedMilestones: [M30, M123, M197]
---

# M198 Network Portal 预约生命周期（改约/取消）

## 目标

在 M197 Network Portal propose/confirm 之上，交付改约与取消写切片：按可信
`X-Network-Context` 对本网点预约执行 reschedule/cancel，失败关闭跨网点与伪造上下文。

## 范围与非目标

- 范围：
  - `POST /api/v1/network-portal/appointments/{appointmentId}:reschedule`；
  - `POST /api/v1/network-portal/appointments/{appointmentId}:cancel`；
  - ADR-036：生命周期适配器委托 `AppointmentService.reschedule` / `cancel`；
  - Core OpenAPI `0.90.0`；复用 Flyway V097 `networkPortal.manageAppointment`（无新迁移）；
  - Admin Web Network Portal 任务页改约/取消动作；
  - PostgreSQL IT、MVC Security、ArchitectureTest、Admin E2E spec。
- 明确不做：
  - `:mark-no-show` / ContactAttempt Network 适配器；
  - 改派、资料补传、离线工作包；
  - ORGANIZATION SavedView、Consumer Identity、完整 product/03。

## 事实源

- ADR-036
- ADR-035 / M197 Portal 门禁与能力种子
- M30/M123 Admin Appointment reschedule/cancel

## 设计要点

- Body 与 Admin reschedule/cancel 同形；`If-Match` 来自列表/回执 aggregateVersion；
- 鉴权顺序：解析上下文 → ACTIVE NetworkMembership → NETWORK
  `networkPortal.manageAppointment` → 预约任务 ACTIVE NETWORK 责任=上下文网点 → 委托 Appointment；
  底层另需 `appointment.manage`（改约）/`appointment.cancel`（取消）；
- 改约后状态回到 PROPOSED，须重新确认；取消为终态；
- 同幂等键重放成功；跨网点/伪造上下文失败关闭。

## 已实现

- [x] ADR-036
- [x] OpenAPI Core `0.90.0`
- [x] 复用 Flyway V097（无新迁移；097/99）
- [x] `NetworkPortalAppointmentService` reschedule/cancel
- [x] Admin Web 改约/取消 + E2E spec
- [x] PostgresIT / Security MVC / ArchitectureTest

## 明确未实现

- 爽约 / 联系尝试 Network 写；
- 完整 Network Portal 产品 UI / 设计系统；
- 离线工作包。

## 工程证据

- OpenAPI：`serviceos-core-v1.yaml` 0.90.0
- Flyway：V097（无 M198 新迁移）
- IT：`NetworkPortalAppointmentLifecyclePostgresIT`
- MVC：`NetworkPortalAppointmentControllerSecurityTest`
- E2E：`network-portal-appointment-lifecycle.spec.ts`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs contracts arch
./mvnw -pl serviceos-backend -am test \
  -Dtest=ArchitectureTest,NetworkPortalAppointmentPostgresIT,NetworkPortalAppointmentLifecyclePostgresIT,NetworkPortalAppointmentControllerSecurityTest,AppointmentPostgresIT
./mvnw -pl serviceos-contracts -am test
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
