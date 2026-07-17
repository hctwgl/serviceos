---
title: M197 Network Portal 预约协作
status: Implemented
milestone: M197
lastUpdated: 2026-07-17
relatedMilestones: [M30, M123, M185, M194, M196]
---

# M197 Network Portal 预约协作

## 目标

在 M194 Network Portal 只读与 M196 指派师傅之上，交付 Network Portal 最小预约写切片：
按可信 `X-Network-Context` 为本网点任务提议/确认预约，失败关闭跨网点与 TECHNICIAN 伪装确认。

## 范围与非目标

- 范围：
  - `GET/POST /api/v1/network-portal/tasks/{taskId}/appointments`；
  - `POST /api/v1/network-portal/appointments/{appointmentId}:confirm`；
  - ADR-035：appointment Portal 适配器委托 `AppointmentService`；
  - Core OpenAPI `0.89.0`；Flyway V097 种子 `networkPortal.manageAppointment`；
  - Page Registry `NETWORK.APPOINTMENT` + catalog `page-registry-v6`；
  - Admin Web Network Portal 任务页预约表单与确认；
  - PostgreSQL IT、MVC Security、ArchitectureTest、Admin E2E spec。
- 明确不做：
  - 改约 / 取消 / 爽约 Network 适配器；
  - ContactAttempt Network 适配器；
  - 改派、资料补传、离线工作包；
  - ORGANIZATION SavedView、Consumer Identity、完整 product/03。

## 事实源

- ADR-035
- M30/M123 Appointment propose/confirm；M194 Network Portal 只读；M196 指派师傅
- product/03 `NETWORK.APPOINTMENT` 仅作 pageId/命名指导（产品能力码仍为 Proposed）

## 设计要点

- Body 与 Admin propose/confirm 同形；确认方仅允许 `NETWORK_MEMBER`/`NETWORK`，
  `confirmedPartyRef=principalId`；拒绝 `TECHNICIAN`；
- 鉴权顺序：解析上下文 → ACTIVE NetworkMembership → NETWORK
  `networkPortal.manageAppointment` → 任务 ACTIVE NETWORK 责任=上下文网点 → 委托 Appointment；
- 委托期间底层 `appointment.*` 按 NETWORK scope 鉴权；
- 同幂等键重放成功；他网点任务失败关闭。

## 已实现

- [x] ADR-035
- [x] OpenAPI Core `0.89.0`
- [x] Flyway V097 `networkPortal.manageAppointment`
- [x] `NetworkPortalAppointmentService` / Controller
- [x] Page Registry `NETWORK.APPOINTMENT`
- [x] Admin Web 预约表单 + E2E spec
- [x] PostgresIT / Security MVC / ArchitectureTest

## 明确未实现

- 改约/取消/爽约/联系尝试 Network 写；
- 完整 Network Portal 产品 UI / 设计系统；
- 离线工作包。

## 工程证据

- OpenAPI：`serviceos-core-v1.yaml` 0.89.0
- Flyway：`authorization/V097__seed_network_portal_manage_appointment_capability.sql`
- IT：`NetworkPortalAppointmentPostgresIT`
- MVC：`NetworkPortalAppointmentControllerSecurityTest`
- E2E：`network-portal-appointments.spec.ts`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs contracts arch
./mvnw -pl serviceos-backend -am test \
  -Dtest=ArchitectureTest,NetworkPortalAppointmentPostgresIT,NetworkPortalAppointmentControllerSecurityTest,AppointmentPostgresIT
./mvnw -pl serviceos-contracts -am test
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
