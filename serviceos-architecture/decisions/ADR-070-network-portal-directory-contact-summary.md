---
title: ADR-070：Network Portal 目录页联系尝试服务端摘要字段
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-065-network-portal-workspace-appointment-contact-summary.md
  - decisions/ADR-069-network-portal-directory-appointment-summary.md
---

# ADR-070：Network Portal 目录页联系尝试服务端摘要字段

## 1. 状态与已接受决策

本 ADR 作为 M232 的边界结论，正式接受：

1. 扩展既有路径
   `GET /api/v1/network-portal/work-orders` 与
   `GET /api/v1/network-portal/tasks` 的列表页包装，**不**新增独立路径；
2. 在 `NetworkPortalWorkOrderPage` / `NetworkPortalTaskPage` 新增可选数组字段
   `contactAttempts`：元素字段集与已 Accepted 的
   `WorkOrderWorkspaceContactAttemptSummary` /
   `NetworkPortalWorkspaceContactAttemptSummary` 完全一致
   （无 contactedPartyRef/note/recordingRef/actorId）；
3. soft-gate：NETWORK `networkPortal.manageAppointment`（与 M199/M227/M231 一致）；
   缺能力时省略属性（不得用空数组伪装无权限）；有能力时可为空列表；
4. 范围：工单页覆盖 `items[].taskIds`、任务页覆盖 `items[].taskId`；
5. 编排复用已 Implemented 的 `AppointmentService.listContactAttempts`；
6. OpenAPI **1.0.11 → 1.0.12**；**不**新增 Flyway、**不**新增 pageId（catalog 仍
   `page-registry-v16`）；
7. Admin Web 目录展示「最近联系」列，消费服务端 `contactAttempts`；**不**做客户端 N+1；
8. **不**接受：PII/party/note/recording/actor、写控件、notifications、Portal ACK、新 pageId。

## 2. 上下文

M227 已在工作区证明联系尝试摘要 soft-gate 安全；ADR-069/M231 将「目录
contactAttempts 旁载」列为须另接受切片。本 ADR 将该投影窄接到工单/任务目录页包装。

## 3. 后果

- Core OpenAPI 1.0.12 + 后端编排 + Admin Web + IT/E2E；
- notifications / Portal ACK 仍 deferred。
