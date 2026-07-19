---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR：https://github.com/hctwgl/serviceos/pull/147
- latestMilestone：**M303**
- Flyway：**118 / 120**；OpenAPI：**1.0.40**
- 下一刀：**M304** ASSIGNEE_POLICY 运行时，或将 INTEGRATION Mapping 接入建单主路径

## 已完成

P0 + M297～M302 Connector SPI；**M303** INTEGRATION Mapping 运行时（冻结 Bundle + 白名单 Transform）

## BLOCKED_EXTERNAL

Swift/Xcode、签名真机、吉利 Sandbox、远端 verify.yml

## 下一步入口

- `DefaultIntegrationMappingRuntime` 接入 `InboundCreateWorkOrderPipeline` / BYD adapter
- ASSIGNEE_POLICY 资产 schema → 候选集解释引擎
