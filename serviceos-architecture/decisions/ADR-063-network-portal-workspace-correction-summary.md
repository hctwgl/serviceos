---
title: ADR-063：Network Portal 工作区整改摘要字段
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
  - Evidence Owner
related_adrs:
  - decisions/ADR-051-network-portal-work-order-workspace.md
  - decisions/ADR-052-network-portal-workspace-queue-deeplinks.md
  - decisions/ADR-061-network-portal-workspace-evidence-summary.md
---

# ADR-063：Network Portal 工作区整改摘要字段

## 1. 状态与已接受决策

本 ADR 作为 M225 的边界结论，正式接受：

1. 扩展既有路径 `GET /api/v1/network-portal/work-orders/{workOrderId}/workspace`，
   **不**复用 Admin workspace section APIs；
2. 新增可选数组字段 `corrections`（缺 NETWORK `evidence.read` 时 **省略**，
   不得用空数组伪装无权限）：
   - 元素字段集与已 Accepted 的 `WorkOrderWorkspaceCorrectionCaseSummary` 完全一致
     （含 `resubmissions[]` 摘要；无 createdBy/waiveNote/digest 等）；
   - 范围仅 ACTIVE NETWORK assignment 的 `taskIds`；含全部状态（不仅 OPEN）；
3. soft-gate：NETWORK `evidence.read`（与 M202/M207/M223 一致）；有能力时数组出现
   （无数据则为 `[]`）；编排复用已 Implemented 的 `CorrectionCaseService.listForTask`
   （已支持 NETWORK scope evidence.read）；
4. OpenAPI **1.0.4 → 1.0.5**；**不**新增 Flyway、**不**新增 pageId（catalog 仍
   `page-registry-v16`）；
5. Admin Web 以服务端 `corrections` 替换 M214 仅 OPEN 的客户端 fan-in 列表；
6. **不**接受：`reviews[]`、Portal ACK/close/waive、Admin workspace 复用、
   notifications、独立 NP Review 列表、新 pageId。

## 2. 上下文

product/03 §6.1 要求工作区展示本网点相关「资料和整改」；M223 已交付资料摘要，整改仍停留在
M214 客户端 OPEN fan-in。Admin M90 已 Accepted 非 PII 整改摘要字段；本 ADR 将其窄投影到
NP workspace，闭合该缺口。

## 3. 后果

- Core OpenAPI 1.0.5 + 后端编排 + Admin Web 展示 + IT/E2E；
- ReviewCase 工作区摘要仍须另接受。
