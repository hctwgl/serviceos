---
title: ADR-046：Network Portal 产能页注册与只读 UI
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-032-network-portal-read-apis.md
  - decisions/ADR-045-network-portal-workbench-enrichment.md
---

# ADR-046：Network Portal 产能页注册与只读 UI

## 1. 状态与已接受决策

本 ADR 作为 M208 的边界与授权结论，正式接受：

1. 注册 product/03 页面 `NETWORK.CAPACITY` 并交付 Admin Web `/network-portal/capacity`
   只读列表；**不**新建 portal 模块、**不**新增 HTTP 路径；
2. **复用**既有 Accepted API（ADR-032 / M194）：
   `GET /api/v1/network-portal/capacity`（`X-Network-Context` + NETWORK `networkTask.read`）；
3. **能力**：不新增 capability 种子；页面能力列表 = `networkTask.read`
   （**不**种子 Proposed 产品名 `networkCapacity.read`）；
4. **展示字段**：仅消费已有 `NetworkPortalCapacityItem`
   （businessType、occupied/max/available、**version**、updatedAt）与页级 `asOf`；
5. Page Registry：catalog → `page-registry-v15`；
6. 工作台 capacity 区块深链至 `/network-portal/capacity`；
7. Core OpenAPI **保持 `0.99.0`**（无契约变更）；Flyway **仍 100/102**；
8. **不**接受：`CapacityAdjustmentRequest`、产能写、预占/停派原因/签约比例/评分字段发明、
   SLA 风险计数、Portal ACK。

## 2. 上下文

M194 已交付 capacity HTTP；M207 工作台嵌入摘要但未注册独立产能页。product/03 页面目录
含 `NETWORK.CAPACITY`，属 Accepted 读 API 上的最后一页壳缺口。

## 3. 后果

- Admin Web 独立产能页 + 导航；
- 产能申请/写若需要，须另接受切片与领域模型。
