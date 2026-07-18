---
title: ADR-054：Network Portal 工作区当前师傅 fan-in
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-051-network-portal-work-order-workspace.md
  - decisions/ADR-052-network-portal-workspace-queue-deeplinks.md
  - decisions/ADR-053-network-portal-workspace-appointment-fanin.md
---

# ADR-054：Network Portal 工作区当前师傅 fan-in

## 1. 状态与已接受决策

本 ADR 作为 M216 的边界结论，正式接受：

1. 在 M213～M215 工作区之上，对既有 Accepted API 做 **客户端 fan-in**：
   - `GET /api/v1/network-portal/technicians`（M194；`technician.readOwnNetwork`）
2. 以工作区头/`tasks[].technicianId` 与 `technicianProfileId` 匹配，解析 `displayName` /
   `membershipId`；渲染「当前师傅」摘要并深链
   `/network-portal/technicians` 与
   `/network-portal/technicians/memberships/{membershipId}`（M212）；
3. 未指派任务可深链 `/network-portal/tasks?taskId=`（复用 M214 水合）引导指派；
4. 可选：对 M215 已拉取的 `Appointment.revisions[current].window` 仅展示
   `start/end/timezone/estimatedDurationMinutes`；**禁止**渲染 `addressRef` / note / 客户 PII；
5. 缺 `technician.readOwnNetwork`（或 technicians 列表 403）时 **省略**「当前师傅」区块，
   任务表仍可显示原始 `technicianId`（不得用假名伪装）；
6. **不**新增 HTTP 路径/字段、**不**升 OpenAPI、**不**新增 Flyway、**不**新增 pageId
   （catalog **保持** `page-registry-v16`；OpenAPI **保持** `1.0.0`）；
7. **不**接受：SLA/Visit/表单 DTO、Admin workspace 复用、客户 PII、Portal ACK、
   notifications、写命令嵌入工作区。

## 2. 上下文

product/03 §6.1 要求工作区展示「当前师傅和预约」。M215 已补齐预约/联系 fan-in；师傅侧
工作区仍只显示 UUID。M194 technicians 列表已返回安全字段 `displayName` /
`technicianProfileId` / `membershipId`，可零契约推进。

## 3. 后果

- Admin Web 工作区师傅摘要 + 预约窗口只读 enrichment + E2E；
- SLA/Visit/表单摘要仍须另接受字段切片（无 Network Portal 读 API，禁止发明）。
