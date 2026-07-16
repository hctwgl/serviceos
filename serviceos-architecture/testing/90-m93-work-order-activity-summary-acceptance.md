---
title: M93 工单最近活动摘要验收矩阵
status: Implemented
milestone: M93
---

# M93 工单最近活动摘要验收矩阵

| 编号 | 场景 | 预期 |
|---|---|---|
| M93-01 | 有 workOrder.read 且时间线有数据 | 返回与 `/timeline` 第一页相同的最近条目和顺序 |
| M93-02 | 默认/边界 limit | 默认 5；1～20；越界 VALIDATION_FAILED |
| M93-03 | cursor 非空 | 400 VALIDATION_FAILED，不建立第二分页通道 |
| M93-04 | freshness | meta freshness 与时间线一致，并返回 projection checkpoint |
| M93-05 | 安全最小化 | 复用无 payload/PII/free-text 的 WorkOrderTimelineItem |
| M93-06 | 工程门禁 | OpenAPI 0.63.0、无新 Flyway、PostgreSQL/MVC/Contract/Client/ArchitectureTest、L3 |
