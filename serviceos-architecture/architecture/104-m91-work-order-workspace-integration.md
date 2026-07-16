---
title: M91 工单工作区集成区块
status: Implemented
milestone: M91
---

# M91 工单工作区集成区块

## 1. 目标

扩展 API-06 §5 `workspace/sections/{section}` Accepted 范围，增加 `INTEGRATION`
实时组合按需加载。

## 2. 接受范围

- 入站仅列出明确 `result_type=WORK_ORDER` 且 `result_id=workOrderId` 的 Envelope；
- 外发列出 `source_work_order_id=workOrderId` 的 Delivery 及安全执行摘要；
- 入口复用 `workOrder.read`；入站、外发分别要求 `integration.readInbound` /
  `integration.readOutbound` 与实时 Project Scope；
- 缺权时对应半边为 null，不把整个工作区打成 403；
- 不返回对象存储引用、payload/signature、operator、幂等键、重放 reason/approvalRef/requestedBy。

审核回调批次未直接记录 WorkOrder 结果引用，本切片不得猜测归属，明确不纳入 inboundEnvelopes。

## 3. 组合事实

| 子集 | 来源 |
|---|---|
| inboundEnvelopes | `integration::api` `InboundMessageQueryService.listForWorkOrder` |
| outboundDeliveries | `integration::api` `OutboundDeliveryService.listForWorkOrder` |

顶层 `sectionAvailability.INTEGRATION`：两边缺权 → UNAVAILABLE；两边空 → EMPTY；
否则 AVAILABLE。limit 分别截断两个子集；本切片不提供深分页 cursor。

## 4. 契约与数据库

- Core OpenAPI **0.61.0**；
- Flyway **V079** 增加外发 Delivery 工单稳定查询索引。

## 5. 明确未实现

`FACTS_CALCULATIONS`、审核回调批次到工单的额外归属规则、专项集成队列、SavedView、
Portal、区块持久化投影。
