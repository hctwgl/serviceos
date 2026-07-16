---
title: M85 工单工作区只读组合快照验收矩阵
status: Implemented
milestone: M85
---

# M85 工单工作区只读组合快照验收矩阵

| ID | 场景 | 期望 |
|---|---|---|
| M85-01 | 有未终态 Task | 返回 header、currentTaskSummary、allowedActionLink、meta |
| M85-02 | 无未终态 Task | currentTaskSummary/allowedActionLink 为 null；TASKS=EMPTY 或 AVAILABLE |
| M85-03 | 缺 workOrder.read / 跨租户 | 403 或 404，与 M68 一致 |
| M85-04 | 缺 sla.read | slaSummary=null，sectionAvailability.SLA=UNAVAILABLE；工作区仍 200 |
| M85-05 | 无客户 PII | 响应不含 customerName/mobile/address/VIN |
| M85-06 | 工程门禁 | OpenAPI 0.56.0、无新 Flyway、PostgreSQL/MVC/Contract/Client/ArchitectureTest、L3 |

不验收 sections 按需加载、队列、SavedView、Portal。
