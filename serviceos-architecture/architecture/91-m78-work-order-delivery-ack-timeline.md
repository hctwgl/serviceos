---
title: M78 工单外发交付确认与恢复时间线事件合并
status: Implemented
milestone: M78
---

# M78 工单外发交付确认与恢复时间线事件合并

## 1. 目标

为 OutboundDelivery 增加公开最小查询端口 `DeliveryTimelineContextQuery`，并在既有工单时间线投影上
合并：

- `integration.outbound-delivery-acknowledged@v1`
- `integration.outbound-delivery-recovered@v1`
- `integration.outbound-delivery-replay-requested@v1`

不实现 Broker checkpoint / 投影重建（DATA-06 仍为 Proposed）。

## 2. 模块边界

- `integration::api` 暴露 `DeliveryTimelineContext(deliveryId, projectId, workOrderId)`；
- 查询只读 `int_outbound_delivery` 三列身份，不加载 attempt/ack/replay 图，不提供用户授权；
- `readmodel` 通过 `allowedDependencies` 依赖 `integration::api`；
- 载荷 `projectId` 存在时必须与权威 Delivery 一致；缺少 Delivery 失败关闭；
- 不投影 orderCode、digest、reason、approvalRef、凭据或签名。

## 3. 映射

| 事件 | outcome | actor |
|---|---|---|
| acknowledged | `ACKNOWLEDGED` | null |
| recovered | `RECOVERED` | null |
| replay-requested | `REPLAY_REQUESTED` | `requestedBy` |

## 4. 契约

Core OpenAPI 0.49.0 扩展 timeline `x-extensible-enum`。无新 Flyway（V075 已含 `DELIVERY`）。

## 5. 明确未实现

exception.acknowledged、试算/结算、checkpoint/重建、Portal。
