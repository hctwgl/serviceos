---
title: M327 PRICING 履约事实与 CalculationSnapshot
status: Implemented
milestone: M327
lastUpdated: 2026-07-19
relatedMilestones: [M309, M326]
---

# M327 PRICING 履约事实与 CalculationSnapshot

## 目标

`workorder.fulfilled` 后从冻结 Bundle 提取最小履约事实，调用 `PricingRuntime.resolve`，
持久化 SHADOW `CalculationSnapshot`；不落账、不创建结算单。

## 范围与非目标

- 范围：
  - Flyway `V125`：`cfg_fulfillment_fact` / `cfg_calculation_snapshot`
  - `WorkOrderConfigurationBindingQuery` 公开端口
  - `workorder.fulfilled` → `WorkOrderFulfilledPricingHandler`（workflow）
  - `PricingCalculationSnapshotService`：Inbox + 事实 + Runtime + SHADOW 快照 + 审计
  - 幂等：Inbox `configuration.pricing.workorder-fulfilled.v1`；
    Snapshot `(tenant, eventId, pricingKey)`
- 明确不做：
  - 结算/对账/Statement/Adjustment
  - 完整 FactDefinition / CalculationRun 状态机
  - workflow `pricingRef` 节点冻结
  - OpenAPI / Admin 计价工作台
  - AUTHORITATIVE 模式

## 已实现

- 履约事实 + SHADOW 快照 + PostgreSQL IT

## 明确未实现

- 落账、对账结算、Admin 工作台、动态公式/税务

## 验证命令

```bash
bash scripts/agent-verify.sh it PricingCalculationSnapshotPostgresIT
bash scripts/agent-verify.sh test ArchitectureTest,DefaultPricingRuntimeTest
```
