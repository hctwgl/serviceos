---
title: ADR-067：Network Portal 工作区审核案例服务端摘要字段
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
  - Evidence Owner
related_adrs:
  - decisions/ADR-051-network-portal-work-order-workspace.md
  - decisions/ADR-063-network-portal-workspace-correction-summary.md
  - decisions/ADR-066-network-portal-workspace-technician-summary.md
---

# ADR-067：Network Portal 工作区审核案例服务端摘要字段

## 1. 状态与已接受决策

本 ADR 作为 M229 的边界结论，正式接受：

1. 扩展既有路径 `GET /api/v1/network-portal/work-orders/{workOrderId}/workspace`，
   **不**复用 Admin workspace；
2. 新增可选数组字段 `reviews`：元素字段集与已 Accepted 的
   `WorkOrderWorkspaceReviewCaseSummary` /
   `WorkOrderWorkspaceReviewDecisionSummary` 完全一致
   （无 note/approvalRef/decidedBy/digest）；
3. soft-gate：NETWORK `evidence.read`（与 M223/M225 一致）；
   缺能力时省略属性（不得用空数组伪装无权限）；有能力时可为空列表；
4. 范围仅 ACTIVE NETWORK assignment 的 `taskIds`；含全部状态；
5. 编排复用已 Implemented 的 `ReviewCaseService.listForTask`，并使其与
   CorrectionCase 一致地接受 NETWORK scope `evidence.read`（PROJECT+NETWORK 并入）；
6. OpenAPI **1.0.8 → 1.0.9**；**不**新增 Flyway、**不**新增 pageId（catalog 仍
   `page-registry-v16`）；
7. Admin Web 展示服务端 `reviews`；深链仅任务水合 / 整改 `sourceReviewCaseId` 旁注，
   **不**发明独立 NP Review 详情页；
8. **不**接受：独立 NP Review 队列/详情 API、Portal ACK/decide、Admin Review 深链、
   note/approvalRef/decidedBy、notifications、新 pageId。

## 2. 上下文

ADR-063 将 ReviewCase 工作区摘要列为须另接受切片；Admin M90/M96 已 Accepted
非 PII 审核摘要字段集。本 ADR 将该投影窄接到 NP workspace，闭合
`REVIEWS_CORRECTIONS` 的 reviews 半侧。

## 3. 后果

- Core OpenAPI 1.0.9 + 后端编排（含 ReviewCase NETWORK read 对齐）+ Admin Web + IT/E2E；
- 独立 NP Review 页面与 Portal ACK 仍 deferred。
