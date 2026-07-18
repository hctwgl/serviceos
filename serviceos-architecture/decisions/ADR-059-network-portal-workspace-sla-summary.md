---
title: ADR-059：Network Portal 工作区薄 SLA 摘要字段
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
  - SLA Owner
related_adrs:
  - decisions/ADR-051-network-portal-work-order-workspace.md
  - decisions/ADR-045-network-portal-workbench-capability-gated-counts.md
---

# ADR-059：Network Portal 工作区薄 SLA 摘要字段

## 1. 状态与已接受决策

本 ADR 作为 M221 的边界结论，正式接受：

1. 扩展既有路径 `GET /api/v1/network-portal/work-orders/{workOrderId}/workspace`，
   **不**复用 Admin `GET /work-orders/{id}/workspace`；
2. 新增可选字段 `slaSummary`（缺 NETWORK `sla.read` 时 **省略**，不得用 0 伪装无权限）：
   - `openCount`：本网点 ACTIVE `taskIds` 上状态 ∈ {RUNNING, BREACHED} 的实例数
   - `breachedCount`：同上集合中状态 = BREACHED 的实例数
3. 基座门禁不变：`X-Network-Context` + ACTIVE membership + NETWORK `networkTask.read`
   + ACTIVE NETWORK ServiceAssignment；SLA 为 soft-gate enrichment；
4. SLA 查询经 `sla::api` 新增 NETWORK 作用域 list（`networkCapability(sla.read)`），
   再按工作区 `taskIds` 过滤；OpenAPI **1.0.0 → 1.0.1**；
5. **不**新增 Flyway、**不**新增 pageId（catalog 仍 `page-registry-v16`）；
6. **不**接受：Visit/表单摘要、工作台 SLA 风险计数、Admin workspace 复用、客户 PII、
   SLA 详情/segments/deeplink、BUSINESS 时钟扩展。

## 2. 上下文

product/03 §6.1 与 status §5 要求工作区 enrichment；ADR-051～058 明确禁止在无字段切片时
发明 SLA DTO。本 ADR 接受最小非 PII 两计数，闭合该缺口。

## 3. 后果

- Core OpenAPI 1.0.1 + 后端编排 + Admin Web 展示 + IT/E2E；
- Visit/表单仍须另接受。
