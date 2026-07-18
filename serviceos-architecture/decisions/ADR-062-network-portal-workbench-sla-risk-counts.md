---
title: ADR-062：Network Portal 工作台薄 SLA 风险计数
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
  - SLA Owner
related_adrs:
  - decisions/ADR-045-network-portal-workbench-capability-gated-counts.md
  - decisions/ADR-059-network-portal-workspace-sla-summary.md
---

# ADR-062：Network Portal 工作台薄 SLA 风险计数

## 1. 状态与已接受决策

本 ADR 作为 M224 的边界结论，正式接受：

1. 扩展既有路径 `GET /api/v1/network-portal/workbench`，**不**新增独立 SLA 队列 API；
2. 新增可选字段 `slaSummary`（缺 NETWORK `sla.read` 时 **省略**，不得用 0 伪装无权限）：
   - 字段集与 M221 `NetworkPortalWorkOrderWorkspaceSlaSummary` 完全一致
     （`openCount` / `breachedCount`）；
   - `openCount`：本网点全部 ACTIVE `taskIds` 上状态 ∈ {RUNNING, BREACHED} 的实例数；
   - `breachedCount`：同上集合中状态 = BREACHED 的实例数；
3. 基座门禁不变：`X-Network-Context` + ACTIVE membership + NETWORK `networkTask.read`；
   SLA 为 soft-gate enrichment（与 M207 其余计数一致）；
4. 复用已 Implemented 的 `SlaQueryService.listForWorkOrderOnNetwork`，按 ACTIVE 工单
   fan-in 后过滤 `taskIds`；OpenAPI **1.0.3 → 1.0.4**；
5. **不**新增 Flyway、**不**新增 pageId（catalog 仍 `page-registry-v16`）；
6. **不**接受：「即将超时」时间窗策略发明、SLA 实例详情/segments/deeplink、
   notifications、Portal ACK、Admin workspace/SLA 队列复用、BUSINESS 时钟扩展、
   Technician TASK.DETAIL。

## 2. 上下文

product/03 §4 要求工作台首屏呈现即将超时/已超时风险；ADR-045 明确 defer SLA 风险计数。
M221 已 Accepted 同语义两计数与 NETWORK `sla.read` soft-gate；本 ADR 将其投影到工作台
跨 ACTIVE 工单聚合，闭合 status §5 候选。

## 3. 后果

- Core OpenAPI 1.0.4 + 后端编排 + Admin Web 展示 + IT/E2E；
- due-soon 时间窗与 SLA 详情仍须另接受。
