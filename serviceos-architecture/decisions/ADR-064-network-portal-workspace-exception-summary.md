---
title: ADR-064：Network Portal 工作区运营异常摘要字段
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
  - Operations Owner
related_adrs:
  - decisions/ADR-051-network-portal-work-order-workspace.md
  - decisions/ADR-052-network-portal-workspace-queue-deeplinks.md
  - decisions/ADR-063-network-portal-workspace-correction-summary.md
---

# ADR-064：Network Portal 工作区运营异常摘要字段

## 1. 状态与已接受决策

本 ADR 作为 M226 的边界结论，正式接受：

1. 扩展既有路径 `GET /api/v1/network-portal/work-orders/{workOrderId}/workspace`，
   **不**复用 Admin workspace；
2. 新增可选数组字段 `exceptions`（缺 NETWORK `operations.exception.read` 时 **省略**，
   不得用空数组伪装无权限）：
   - 元素字段集与已 Accepted 的 `NetworkPortalExceptionItem` 完全一致
     （`allowedActions` 恒为空；无 acknowledgementNote/操作者）；
   - 范围仅 ACTIVE NETWORK assignment 的 `taskIds`；含全部状态（不仅 OPEN）；
3. soft-gate：NETWORK `operations.exception.read`（与 M203/M207/M214 一致）；
   编排复用已 Implemented 的 `OperationalExceptionWorkbenchService.listForTask`
   （已支持 NETWORK scope）；
4. OpenAPI **1.0.5 → 1.0.6**；**不**新增 Flyway、**不**新增 pageId（catalog 仍
   `page-registry-v16`）；
5. Admin Web 以服务端 `exceptions` 替换 M214 仅 OPEN 的客户端 fan-in 列表；
6. **不**接受：Portal ACK/resolve、发明 Admin exception-item 新 schema、
   `exceptionSummary`-only、notifications、预约/联系服务端摘要（另切片）、新 pageId。

## 2. 上下文

product/03 §6.1 要求工作区展示本网点可处理的异常；M214 仅客户端 OPEN fan-in。Admin
工作区仅有 `exceptionSummary.openCount`，无条目摘要；本 ADR 接受将已 Accepted 的
`NetworkPortalExceptionItem` 窄投影到 NP workspace，闭合该缺口。

## 3. 后果

- Core OpenAPI 1.0.6 + 后端编排 + Admin Web 展示 + IT/E2E；
- 预约/联系服务端摘要与 Portal ACK 仍须另接受。
