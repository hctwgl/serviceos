---
title: ADR-065：Network Portal 工作区预约/联系服务端摘要字段
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
  - Appointment Owner
related_adrs:
  - decisions/ADR-051-network-portal-work-order-workspace.md
  - decisions/ADR-053-network-portal-workspace-appointment-fanin.md
  - decisions/ADR-064-network-portal-workspace-exception-summary.md
---

# ADR-065：Network Portal 工作区预约/联系服务端摘要字段

## 1. 状态与已接受决策

本 ADR 作为 M227 的边界结论，正式接受：

1. 扩展既有路径 `GET /api/v1/network-portal/work-orders/{workOrderId}/workspace`，
   **不**复用 Admin workspace；
2. 新增可选数组字段：
   - `appointments`：元素字段集与已 Accepted 的 `WorkOrderWorkspaceAppointmentSummary` 完全一致；
   - `contactAttempts`：元素字段集与已 Accepted 的 `WorkOrderWorkspaceContactAttemptSummary` 完全一致
     （无 contactedPartyRef/note/recordingRef/actorId）；
3. soft-gate：NETWORK `networkPortal.manageAppointment`（与 M215/ADR-053 一致）；
   缺能力时 **同时省略**两属性（不得用空数组伪装无权限）；有能力时可为空列表；
4. 范围仅 ACTIVE NETWORK assignment 的 `taskIds`；预约另按可信上下文 `networkId`
   过滤 `assignedNetworkId`（对齐 M222 Visit networkId 过滤）；
5. 编排复用已 Implemented 的 `AppointmentService.listByTask` /
   `listContactAttempts`（与 Admin 工作区摘要映射同构）；
6. OpenAPI **1.0.6 → 1.0.7**；**不**新增 Flyway、**不**新增 pageId（catalog 仍
   `page-registry-v16`）；
7. Admin Web 以服务端字段替换 M215 客户端 fan-in；保留既有 testid 与 task 深链；
8. **不**接受：完整 Appointment/ContactAttempt、写控件嵌入、客户 PII、Admin workspace
   复用、Portal ACK、notifications、师傅 displayName 服务端摘要（M216 仍客户端）、
   新 pageId。

## 2. 上下文

product/03 §6.1 要求工作区展示预约；M215 仅客户端 fan-in。Admin 工作区已 Accepted
预约/联系摘要字段集；ADR-064 将服务端摘要列为须另接受切片。本 ADR 将该投影窄接到
NP workspace，闭合该缺口。

## 3. 后果

- Core OpenAPI 1.0.7 + 后端编排 + Admin Web 展示 + IT/E2E；
- 师傅 displayName 服务端摘要与 Portal ACK 仍须另接受。
