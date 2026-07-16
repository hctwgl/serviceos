---
title: M77 工单外发交付与异常闭环时间线事件合并
status: Implemented
milestone: M77
---

# M77 工单外发交付与异常闭环时间线事件合并

## 1. 目标

在既有工单时间线投影上合并：

- `integration.outbound-delivery-created@v1`（载荷含 `sourceWorkOrderId`）；
- `operational.exception.resolved@v2`（经 `sourceTaskId` + `TaskTimelineContextQuery`）。

不新增跨模块 Delivery 查询端口，因此不合并仅有 `deliveryId` 的 acknowledged/recovered。

## 2. 映射

| 事件 | category | resourceType | outcome |
|---|---|---|---|
| `integration.outbound-delivery-created@v1` | `DELIVERY` | `OutboundDelivery` | `CREATED` |
| `operational.exception.resolved@v2` | `EXCEPTION` | `OperationalException` | `resolutionCode` |

不保存 externalOrderCode、digest、签名、凭据或自由文本。

## 3. 数据库与契约

- V075 expand `DELIVERY` / `EXCEPTION`；
- Core OpenAPI 0.48.0 扩展 `x-extensible-enum`；
- `supports` 接受 `operational.exception.resolved` schemaVersion=2。

## 4. 明确未实现

delivery acknowledged/recovered/replay、exception acknowledged、无 task 链接的异常、
checkpoint/重建、Portal。
