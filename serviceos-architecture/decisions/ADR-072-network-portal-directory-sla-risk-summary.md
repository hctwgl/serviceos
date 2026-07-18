---
title: ADR-072 Network Portal 目录页 SLA 风险服务端摘要旁载
status: Accepted
date: 2026-07-17
milestone: M234
---

# ADR-072：Network Portal 目录页 SLA 风险服务端摘要旁载（目录「SLA 风险」列 · 承接 M224 / M233）

- Status: Accepted
- Date: 2026-07-17
- Relates to: ADR-062（工作台 slaSummary）、ADR-059（工作区 slaSummary）、ADR-068～071（目录旁载程序）、product/03 §5「SLA 风险」、architecture/247、testing/231、M234

## Context

M221/M224 已接受薄 SLA 计数语义（`openCount`/`breachedCount`，NETWORK `sla.read`）。
M230～M233 已将师傅/预约/联系/整改旁载到目录页。product/03 §5 仍要求目录「SLA 风险」列。
直接复用无 join key 的 `NetworkPortalWorkOrderWorkspaceSlaSummary` 无法服务多行目录。

## Decision

1. 扩展 work-orders/tasks 页包装可选 `slaRiskSummaries[]`（新 schema
   `NetworkPortalDirectorySlaRiskSummary`：`workOrderId` + 可选 `taskId` + 计数）。
2. 软门禁 NETWORK `sla.read`；缺权省略；有权且无开放风险返回 `[]`。
3. 工单目录：按 `workOrderId` 聚合本页 taskIds，`taskId=null`；仅 `openCount>0`。
4. 任务目录：按 `taskId` 展开，仅 `openCount>0`。
5. 计数语义同 M221/M224（RUNNING∪BREACHED / BREACHED）。
6. OpenAPI `1.0.13 → 1.0.14`；catalog v16；无 Flyway；无新 pageId。
7. **明确未实现**：即将超时时间窗、完整 SlaInstance DTO、目录 evidence、notifications、Portal ACK。

## Consequences

- 目录 SLA 风险列可零额外 HTTP 渲染。
- 与工作台/工作区同权、同计数语义。
