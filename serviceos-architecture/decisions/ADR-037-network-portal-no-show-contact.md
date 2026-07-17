---
title: ADR-037：Network Portal 爽约与联系尝试适配器
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
  - decisions/ADR-036-network-portal-appointment-lifecycle.md
---

# ADR-037：Network Portal 爽约与联系尝试适配器

## 1. 状态与已接受决策

本 ADR 作为 M199 的边界与授权结论，正式接受：

1. Network Portal **写命令**扩展「爽约判定 / 联系尝试」；**不**新建 portal 模块、**不**新建并行预约/联系状态机；
2. HTTP（Core OpenAPI `0.91.0`）：
   - `POST /api/v1/network-portal/appointments/{appointmentId}:mark-no-show`
   - `GET /api/v1/network-portal/tasks/{taskId}/contact-attempts`
   - `POST /api/v1/network-portal/tasks/{taskId}/contact-attempts`
3. **请求体**：与 Admin mark-no-show / RecordContactAttempt 同形
   （`MarkAppointmentNoShowRequest` / `RecordContactAttemptRequest`）；
4. **上下文与门禁**：复用 ADR-035——`X-Network-Context`、ACTIVE `NetworkMembership`、
   NETWORK scope `networkPortal.manageAppointment`、任务/预约 ACTIVE NETWORK 责任=上下文网点；
5. **能力**：继续复用 V097 种子 `networkPortal.manageAppointment`；**不**新增 Flyway；
   委托时仍要求 NETWORK scope 底层能力——`appointment.read`、
   `appointment.manage`（爽约）、`appointment.recordContact`（联系写入）；与 M196～M198 双能力模式一致；
6. **爽约语义**：仅 CONFIRMED 且窗口已结束的预约可标记 `NO_SHOW`；`If-Match` 乐观锁；
   窗口未结束返回 `APPOINTMENT_WINDOW_NOT_ENDED`；版本冲突返回 `APPOINTMENT_VERSION_CONFLICT`；
7. **联系事实**：ContactAttempt 不可变；actor 来自 JWT；不接受客户端伪造操作者；
8. **编排归属**：扩展 `appointment` 模块既有 Portal 适配器；不复制 Appointment/Contact 领域逻辑；
9. **不**接受：改派、资料补传 / `evidence.submitOnBehalf`、Visit check-in、离线工作包、
   ORGANIZATION SavedView、Consumer Identity、完整 product/03。

## 2. 上下文

M197/M198 已交付 Network Portal 预约提议/确认/改约/取消；Admin mark-no-show 与
ContactAttempt 领域命令已 Implemented。网点需要在同一可信上下文内记录本网点任务联系事实，
并对已结束窗口的已确认预约标记爽约，失败关闭跨网点与伪造上下文。

## 3. 后果

- OpenAPI 从 `0.90.0` 升至 `0.91.0`；Flyway 保持 `097/99`；
- ArchitectureTest 模块边界不变；
- Admin Web Network Portal 任务页增加爽约与联系尝试动作；
- 改派/资料 Network 写若需要，须另接受切片。
