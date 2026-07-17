---
title: ADR-055：Network Portal 目录页师傅 fan-in 与工作台基数深链
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-054-network-portal-workspace-technician-fanin.md
  - decisions/ADR-052-network-portal-workspace-queue-deeplinks.md
---

# ADR-055：Network Portal 目录页师傅 fan-in 与工作台基数深链

## 1. 状态与已接受决策

本 ADR 作为 M217 的边界结论，正式接受：

1. 将 M216 师傅 fan-in 模式扩展到目录面：
   - `/network-portal/work-orders`：客户端 fan-in
     `GET /api/v1/network-portal/technicians`，解析 `technicianId` → `displayName`；
     展示既有非 PII 列 `projectId` / `effectiveFrom`；工单行保持深链工作区；
   - `/network-portal/tasks`：复用页内已加载 technicians，解析 displayName；
     工单列深链工作区；补充既有列 `stageCode` / `taskKind` / `projectId`；
2. 工作台基数计数深链：`activeWorkOrderCount` → `/work-orders`、
   `activeTaskCount` → `/tasks`、`activeTechnicianCount` → `/technicians`
   （与 M207 enrichment 计数深链对齐）；
3. 详情页残余深链：整改 `correctionTaskId` → `/tasks?taskId=`；
   异常 `workOrderId` → `/work-orders/{id}`；
4. 缺 `technician.readOwnNetwork` 时工单目录保留原始 technicianId（不省略整表）；
5. **不**新增 HTTP/字段、**不**升 OpenAPI、**不**新增 Flyway、**不**新增 pageId；
6. **不**接受：SLA/Visit/表单 DTO、Admin workspace 复用、客户 PII、notifications、
   列表 N+1 预约 fan-in（预约仍以工作区/任务页为准）。

## 2. 上下文

product/03 §5 目录面仍显示师傅 UUID；M216 已在工作区证明 technicians fan-in 安全。
本切片零契约补齐目录可读性与工作台基数导航。

## 3. 后果

- Admin Web 目录/工作台/详情深链 + E2E；
- SLA/Visit/表单与 `NETWORK.NOTIFICATION` 仍需另接受。
