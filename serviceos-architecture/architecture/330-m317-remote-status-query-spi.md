---
title: M317 远端状态查询 Connector SPI
status: Implemented
milestone: M317
lastUpdated: 2026-07-19
relatedMilestones: [M59, M297, M316]
---

# M317 远端状态查询 Connector SPI

## 目标

为 UNKNOWN OutboundDelivery 提供失败关闭的远端状态探询 SPI：协议不支持则 `NOT_SUPPORTED`，本地 stub 默认 `STILL_UNKNOWN`，不得猜 ACK；观察结果可审计，不自动改写 Delivery。

## 范围

- `RemoteStatusQueryConnector` / `RemoteStatusQueryRequest` / `RemoteStatusQueryResult`
- `RemoteStatusQueryConnectors` 注册表（按 connectorVersion 唯一解析）
- BYD：`NotSupported`（CPIM 无权威查询）
- Geely：本地 stub 默认 `StillUnknown`；测试可覆盖 ACCEPTED/REJECTED
- `POST /outbound-deliveries/{id}:query-remote-status`（USER + `integration.retryUnknownDelivery` + reason）
- Core OpenAPI **1.0.41**

## 明确未实现

- 查询结果自动收敛 Delivery 状态；人工标记已送达/放弃；真实吉利 Sandbox 7.21 联调

## 验证

```bash
bash scripts/agent-verify.sh test RemoteStatusQuerySpiTest,OutboundDeliveryControllerSecurityTest,ArchitectureTest
bash scripts/agent-verify.sh contracts
```
