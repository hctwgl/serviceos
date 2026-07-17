---
title: ADR-035：Network Portal 预约协作适配器复用 Appointment 命令
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
  - decisions/ADR-034-network-portal-assign-technician.md
---

# ADR-035：Network Portal 预约协作适配器复用 Appointment 命令

## 1. 状态与已接受决策

本 ADR 作为 M197 的边界与授权结论，正式接受：

1. Network Portal **写命令**窄切片接受「预约提议/确认」适配器；**不**新建 portal 模块、**不**新建并行预约状态机；
2. HTTP（Core OpenAPI `0.89.0`）：
   - `GET /api/v1/network-portal/tasks/{taskId}/appointments`
   - `POST /api/v1/network-portal/tasks/{taskId}/appointments`
   - `POST /api/v1/network-portal/appointments/{appointmentId}:confirm`
3. **请求体**：propose/confirm 与 Admin 同形；确认方字段受 Portal 约束（见下）；
4. **上下文**：`X-Network-Context` 必填（同 ADR-032）；任务/预约必须对本上下文网点持有
   ACTIVE NETWORK `ServiceAssignment`；
5. **前置失败关闭**：
   - 主体对上下文网点持有 ACTIVE `NetworkMembership`，否则 `PORTAL_CONTEXT_INVALID`；
   - 任务无 ACTIVE NETWORK 责任或不属于上下文网点 → `ACCESS_DENIED`；
   - 伪造/非成员上下文 → `PORTAL_CONTEXT_INVALID`；
6. **确认方身份（关键）**：
   - Network Portal 确认允许 `confirmedPartyType` = `NETWORK_MEMBER` 或 `NETWORK`；
   - `confirmedPartyRef` **必须**等于当前 JWT `principalId`（网点操作者，不是师傅）；
   - **拒绝** `TECHNICIAN`（以及 CUSTOMER 等其它当事方类型）——不得伪装师傅确认；
7. **能力**：种子 `networkPortal.manageAppointment`（HIGH）。Portal 门禁按 NETWORK scope 校验该能力；
   委托 `AppointmentService` 时仍要求 NETWORK scope `appointment.read` /
   `appointment.propose` / `appointment.manage`（与 M196 双能力模式一致）；
8. **编排归属**：`appointment` 模块提供 Portal 写适配器；经 `dispatch::api` 校验 ACTIVE
   NETWORK 责任，经 `network::api` 校验成员关系；不复制预约领域逻辑；
9. Page Registry：`NETWORK.APPOINTMENT`（catalog → `page-registry-v6`）；导航 pageId
   不是授权真相；
10. **不**接受：改约/取消/爽约 Network 适配器、ContactAttempt Network 适配器、改派、资料补传、
    离线工作包、ORGANIZATION SavedView、Consumer Identity。

## 2. 上下文

M194/M196 已交付 Network Portal 只读与指派师傅；Appointment propose/confirm 领域与 Admin HTTP
已 Implemented。网点需要在本网点上下文内提议/确认预约，但不得让客户端自报 networkId，也不得
伪装 TECHNICIAN 确认方。

## 3. 后果

- OpenAPI 从 `0.88.0` 升至 `0.89.0`；Flyway V097 种子 `networkPortal.manageAppointment`；
- ArchitectureTest 保持 `appointment → network::api` / `dispatch::api`；
- Admin Web Network Portal 任务页增加预约表单与确认动作；
- 改约/取消/爽约若需要，须另接受切片。
