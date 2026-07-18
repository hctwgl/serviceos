---
title: M272 REFERENCE_OEM SAMPLE Connector
status: Implemented
milestone: M272
lastUpdated: 2026-07-18
relatedMilestones: [M56, M267, M271]
---

# M272 REFERENCE_OEM SAMPLE Connector

## 目标

在缺少真实第二家协议时，交付明确标记为 REFERENCE / SAMPLE / TBD_EXTERNAL_CONTRACT 的第二连接器，经通用 SPI 管道建单。

## 已实现

- `integration.referenceoem`：SAMPLE HMAC 验签、入站 Controller、委托 `InboundCreateWorkOrderPipeline`
- `clientCode=REFERENCE_OEM`；独立配置绑定；不写领域表
- Security permitAll 仅限 SAMPLE 路径；核心域防依赖门禁保持
- PostgreSQL HTTP IT：建单 + transport 重放

## 明确未实现 / 外部阻塞

- 吉利/广汽真实验签、错误码、Sandbox、脱敏报文：`BLOCKED_EXTERNAL` / `TBD_EXTERNAL_CONTRACT`
- 出站提审/回调、真实资料要求
