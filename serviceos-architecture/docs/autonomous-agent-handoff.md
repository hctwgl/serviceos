---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR：https://github.com/hctwgl/serviceos/pull/147
- latestMilestone：**M302**
- Flyway：**118 / 120**
- OpenAPI：**1.0.40**
- 下一刀：**M303** P2 INTEGRATION Mapping 运行时（冻结 Bundle 加载 + 白名单 Transform）

## 已完成

P0 + M297～M302 Connector SPI 生命周期（出站/回调/取消/更新/Profile/Route）

## BLOCKED_EXTERNAL

Swift/Xcode、签名真机、吉利 Sandbox、远端 verify.yml

## 下一步入口

- `configuration-schemas/integration-v1.schema.json` + 冻结 Bundle asset 加载
- `InboundCreateWorkOrderPipeline` 可选接入 Mapping 运行时
