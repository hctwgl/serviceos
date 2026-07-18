---
title: ADR-053：Network Portal 工作区预约/联系尝试客户端 fan-in
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-051-network-portal-work-order-workspace.md
  - decisions/ADR-052-network-portal-workspace-queue-deeplinks.md
---

# ADR-053：Network Portal 工作区预约/联系尝试客户端 fan-in

## 1. 状态与已接受决策

本 ADR 作为 M215 的边界结论，正式接受：

1. 在 M213/M214 工作区之上，对既有 Accepted API 做 **客户端 fan-in**：
   - `GET /api/v1/network-portal/tasks/{taskId}/appointments`（M197）
   - `GET /api/v1/network-portal/tasks/{taskId}/contact-attempts`（M199）
2. 按工作区 ACTIVE `taskIds` 并行拉取；渲染只读摘要行（id/type/status/channel/result 等
   既有安全字段）并深链 `/network-portal/tasks?taskId=`（复用 M214 水合）；
3. 缺 `networkPortal.manageAppointment`（或任一次 403）时 **省略**预约/联系区块，
   不得用空列表伪装“无权限”；
4. **不**新增 HTTP 路径/字段、**不**升 OpenAPI、**不**新增 Flyway、**不**新增 pageId
   （catalog **保持** `page-registry-v16`；OpenAPI **保持** `1.0.0`）；
5. **不**接受：SLA/Visit/表单 DTO、客户 PII、Portal ACK、notifications、写命令嵌入工作区。

## 2. 上下文

product/03 §6.1 要求工作区展示当前师傅与预约。M197/M199 已交付任务级预约/联系读 API；
工作区 DTO 仍为薄快照。沿用 ADR-052 的 client fan-in 模式即可零契约推进。

## 3. 后果

- Admin Web 工作区预约/联系摘要 + E2E；
- SLA/Visit/表单字段仍须另接受切片。
