---
title: ADR-052：Network Portal 工作区协作队列深链与 query 水合
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-051-network-portal-work-order-workspace.md
  - decisions/ADR-040-network-portal-correction-queue.md
  - decisions/ADR-041-network-portal-exception-queue.md
  - decisions/ADR-047-network-portal-correction-detail.md
---

# ADR-052：Network Portal 工作区协作队列深链与 query 水合

## 1. 状态与已接受决策

本 ADR 作为 M214 的边界结论，正式接受：

1. 在 M213 薄工作区之上，交付 **UI-only** enrichment：任务行深链至
   `/network-portal/tasks?taskId=`、`/network-portal/corrections?taskId=`、
   `/network-portal/exceptions?taskId=`；
2. 上述目标页 **水合** `route.query.taskId`：任务页选中对应任务并加载预约/联系；
   整改/异常页将 `taskId` 传入既有 list 过滤参数；
3. 工作区可对既有 list API 做客户端 fan-in（按工作区 `taskIds` 过滤 OPEN 整改/异常
   摘要行并深链详情）；缺能力（403）时 **省略**区块，不得伪造 0；
4. **不**新增 HTTP 路径/字段、**不**升 OpenAPI、**不**新增 Flyway、**不**新增 pageId
   （catalog **保持** `page-registry-v16`；OpenAPI **保持** `1.0.0`）；
5. **不**接受：SLA/Visit/表单 DTO 发明、客户 PII、Portal ACK、notifications、
   Admin workspace 复用。

## 2. 上下文

M213 工作区已返回 `tasks[].taskId`，且任务深链 URL 已存在，但任务/整改/异常页未消费
`taskId` query。M202/M203 list 已 Accepted 可选 `taskId` 过滤。

## 3. 后果

- Admin Web 深链与水合 + E2E；
- 更完整的 §6.1 区块（SLA/Visit/表单）须另接受字段切片。
