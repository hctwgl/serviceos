---
title: M300 入站取消工单 Connector SPI
status: Implemented
milestone: M300
lastUpdated: 2026-07-19
relatedMilestones: [M56, M267, M279, M297]
---

# M300 入站取消工单 Connector SPI

## 目标

建立通用 `CANCEL_WORK_ORDER` 入站管道，并将 BYD 用户取消订单接入该管道；适配器只做验签/防重放/映射，领域取消经 `WorkOrderCommandService`。

## 范围与非目标

- 范围：
  - `CancelWorkOrderMappedInbound` / `InboundCancelWorkOrderResult`；
  - `InboundCancelWorkOrderPipeline`；
  - `WorkOrderExternalLookup`；
  - BYD `cancel-orders` HTTP 端点；
  - 单元测试 + Postgres IT。
- 明确不做：Update 入站、暂停/恢复、取消审核回传出站、OpenAPI Core 版本 bump（OEM 协议端点）。

## 设计要点

1. 两段式：Envelope/Nonce 先提交；Canonical + cancel 同事务。
2. 定位：`tenant + clientCode + externalOrderCode`；仅 RECEIVED/ACTIVE 可取消。
3. 幂等：取消 businessKey 冲突摘要失败关闭；同摘要重放。
4. 领域事件：依赖 `workorder.cancelled` 驱动工作流级联，不另发明未版本化集成事件。

## 已实现

- SPI + 管道 + BYD 适配器/控制器/Security permitAll；
- `BydCpimCancelOrderHttpPostgresIT`：建单→取消→重放；缺工单失败关闭。

## 明确未实现

- Update/Close/Suspend/Resume 入站；REFERENCE_OEM 取消样本；取消审核出站 SPI。

## 验证命令

```bash
bash scripts/agent-verify.sh test ArchitectureTest,CancelWorkOrderMappedInboundTest,BydCpimCancelOrderMapperTest
bash scripts/agent-verify.sh it BydCpimCancelOrderHttpPostgresIT
```
