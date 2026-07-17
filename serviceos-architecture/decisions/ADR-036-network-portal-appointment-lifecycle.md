---
title: ADR-036：Network Portal 预约生命周期（改约/取消）适配器
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-001-modular-monolith-first.md
  - decisions/ADR-013-reference-engineering-profile-and-module-enforcement.md
  - decisions/ADR-026-portal-context-navigation-in-authorization.md
  - decisions/ADR-032-network-portal-read-apis.md
  - decisions/ADR-035-network-portal-appointments.md
---

# ADR-036：Network Portal 预约生命周期（改约/取消）适配器

## 1. 状态与已接受决策

本 ADR 作为 M198 的边界与授权结论，正式接受：

1. Network Portal **写命令**扩展「改约 / 取消」；**不**新建 portal 模块、**不**新建并行预约状态机；
2. HTTP（Core OpenAPI `0.90.0`）：
   - `POST /api/v1/network-portal/appointments/{appointmentId}:reschedule`
   - `POST /api/v1/network-portal/appointments/{appointmentId}:cancel`
3. **请求体**：与 Admin reschedule/cancel 同形（`RescheduleAppointmentRequest` /
   `CancelAppointmentRequest`）；
4. **上下文与门禁**：复用 ADR-035——`X-Network-Context`、ACTIVE `NetworkMembership`、
   NETWORK scope `networkPortal.manageAppointment`、任务/预约 ACTIVE NETWORK 责任=上下文网点；
5. **能力**：继续复用 V097 种子 `networkPortal.manageAppointment`；**不**新增 Flyway；
   委托时仍要求 NETWORK scope 底层能力——`appointment.read`、
   `appointment.manage`（改约）、`appointment.cancel`（取消）；与 M196/M197 双能力模式一致；
6. **并发**：`If-Match` 乐观锁；版本冲突返回 `APPOINTMENT_VERSION_CONFLICT`；
7. **编排归属**：扩展 `appointment` 模块既有 Portal 适配器；不复制预约领域逻辑；
8. **不**接受：`:mark-no-show` Network 适配器、ContactAttempt Network 适配器、改派、资料补传、
   离线工作包、ORGANIZATION SavedView、Consumer Identity。

## 2. 上下文

M197 已交付 Network Portal propose/confirm；Admin Appointment reschedule/cancel 已 Implemented。
网点需要在同一可信上下文内改约/取消本网点预约，失败关闭跨网点与伪造上下文。

## 3. 后果

- OpenAPI 从 `0.89.0` 升至 `0.90.0`；Flyway 保持 `097/99`；
- ArchitectureTest 模块边界不变；
- Admin Web Network Portal 任务页增加改约/取消动作；
- 爽约/联系尝试若需要，须另接受切片。
