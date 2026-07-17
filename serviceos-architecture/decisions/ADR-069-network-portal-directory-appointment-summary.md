---
title: ADR-069：Network Portal 目录页预约服务端摘要字段
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-065-network-portal-workspace-appointment-contact-summary.md
  - decisions/ADR-068-network-portal-directory-technician-summary.md
---

# ADR-069：Network Portal 目录页预约服务端摘要字段

## 1. 状态与已接受决策

本 ADR 作为 M231 的边界结论，正式接受：

1. 扩展既有路径
   `GET /api/v1/network-portal/work-orders` 与
   `GET /api/v1/network-portal/tasks` 的列表页包装，**不**新增独立路径；
2. 在 `NetworkPortalWorkOrderPage` / `NetworkPortalTaskPage` 新增可选数组字段
   `appointments`：元素字段集与已 Accepted 的
   `WorkOrderWorkspaceAppointmentSummary` /
   `NetworkPortalWorkspaceAppointmentSummary` 完全一致
   （无 revisions/allowedActions/address）；
3. soft-gate：NETWORK `networkPortal.manageAppointment`（与 M197/M227 一致）；
   缺能力时省略属性（不得用空数组伪装无权限）；有能力时可为空列表；
4. 范围：工单页覆盖 `items[].taskIds`、任务页覆盖 `items[].taskId`；
   另按可信 networkId 过滤 `assignedNetworkId`；
5. 编排复用已 Implemented 的 `AppointmentService.listByTask`；
6. OpenAPI **1.0.10 → 1.0.11**；**不**新增 Flyway、**不**新增 pageId（catalog 仍
   `page-registry-v16`）；
7. Admin Web 目录展示预约窗口列，消费服务端 `appointments`；**不**做客户端 N+1；
8. **不**接受：完整 Appointment DTO、写控件、PII、contactAttempts 目录旁载、
   notifications、Portal ACK、新 pageId。

## 2. 上下文

product/03 §5 要求目录展示预约窗口；M227 已在工作区证明预约摘要 soft-gate 安全；
ADR-068/M230 将「列表预约服务端摘要」列为须另接受切片。本 ADR 将该投影窄接到
工单/任务目录页包装。

## 3. 后果

- Core OpenAPI 1.0.11 + 后端编排 + Admin Web + IT/E2E；
- 目录 contactAttempts / notifications / Portal ACK 仍 deferred。
