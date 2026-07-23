---
title: ADR-086：出站提审 Connector SPI
status: Accepted
date: 2026-07-19
---

# ADR-086：出站提审 Connector SPI

## 背景

ADR-085 已将 CREATE_WORK_ORDER 入站归位到通用管道，但 BYD 提审仍在 `BydReviewSubmissionTaskHandler` 内编排 attempt、HTTP、技术 ACK 与本地落账。继续扩展第二家车企出站时，若不抽出 SPI，将复制 Delivery 状态机或把协议判断渗入通用域。

## 决策

1. 在 `integration.spi` 增加 `OutboundSubmissionConnector` 及请求/签名/传输/技术 ACK/`ConnectorFailure` 模型。
2. 在 `integration.application` 提供 `OutboundSubmissionPipeline`，独占：
   - attempt 短事务登记；
   - 事务外单次 `send`；
   - 响应私有存储；
   - Delivery DELIVERED / REJECTED / UNKNOWN / FAILED_FINAL 迁移；
   - DELIVERED 后本地 finalize（可安全重试，不再次 HTTP）。
3. OEM 适配器只实现签名、发送与技术 ACK 解释；不得写 WorkOrder/Task/Evidence 等业务表。
4. 技术 ACK 与业务 ACK 分离：本 SPI 不处理厂端审核回调。
5. UNKNOWN 不得映射为可重发 HTTP；仅 NOT_SENT 可证无副作用时记 FAILED_FINAL。

## 后果

- BYD 提审任务变为薄入口 + `BydOutboundSubmissionConnector`。
- 后续 OEM 出站以新连接器接入同一管道，不复制 Delivery 状态机。
- 回调 SPI、创建面 connector 注册表与 INTEGRATION Mapping 运行时按独立完整任务推进。
