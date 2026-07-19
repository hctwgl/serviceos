---
title: M318 UNKNOWN Delivery 人工确认/放弃
status: Implemented
milestone: M318
lastUpdated: 2026-07-19
relatedMilestones: [M59, M60, M317]
---

# M318 UNKNOWN Delivery 人工确认/放弃

## 目标

为 UNKNOWN OutboundDelivery 提供高风险人工处置：`MANUAL_CONFIRMED`（外部已人工确认）与 `ABANDONED`（放弃）。不得伪装技术 ACKNOWLEDGED，不创建 CLIENT Case/Route。

## 范围

- Flyway **V119**：`int_delivery_manual_disposition` + capability `integration.recordManualOutboundAck`
- `POST /outbound-deliveries/{id}:record-manual-ack`
- Delivery **状态保持 UNKNOWN**；以 disposition 唯一约束保证一次性
- 处置后禁止再次 `:retry`
- 发出 `integration.outbound-delivery-recovered` 以闭环运营异常（多 OEM `sourceTaskType`）
- Core OpenAPI **1.0.42**

## 明确未实现

- 批量 ReplayRequest 审批入口；查询结果自动改写状态；吉利 Sandbox

## 验证

```bash
bash scripts/agent-verify.sh it ManualDispositionPostgresIT
bash scripts/agent-verify.sh contracts
```
