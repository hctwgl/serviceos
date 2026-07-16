---
title: M91 工单工作区集成区块验收矩阵
status: Implemented
milestone: M91
---

# M91 工单工作区集成区块验收矩阵

| 编号 | 场景 | 预期 |
|---|---|---|
| M91-01 | readInbound + CREATE_WORK_ORDER 成功映射 | 返回 inboundEnvelopes，不含原文/Canonical 对象引用与 payload digest |
| M91-02 | readOutbound + Delivery | 返回 outboundDeliveries，不含 operator、幂等键、重放原因/审批引用 |
| M91-03 | 仅 workOrder.read | 顶层 INTEGRATION=UNAVAILABLE |
| M91-04 | 单边缺权 | 缺权半边 null；有权半边保持权威空/数据语义 |
| M91-05 | cursor 非空 | 400 VALIDATION_FAILED |
| M91-06 | 工程门禁 | OpenAPI 0.61.0、V079/81 migrations、PostgreSQL/MVC/Contract/Client/ArchitectureTest、L3 |
