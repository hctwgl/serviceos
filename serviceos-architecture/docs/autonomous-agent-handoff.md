---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR：https://github.com/hctwgl/serviceos/pull/147
- latestMilestone：**M306**
- Flyway：**118 / 120**；OpenAPI：**1.0.40**
- 下一刀：**M307** NOTIFICATION 运行时

## 已完成

P0 + M297～M302 Connector SPI；M303～M306 配置运行时（INTEGRATION/ASSIGNEE/DISPATCH）

## BLOCKED_EXTERNAL

Swift/Xcode、签名真机、吉利 Sandbox、远端 verify.yml

## 下一步入口

- NOTIFICATION schema → 事件触发/收件人/模板/通道 SPI/幂等/UNKNOWN
- 缺凭据时用本地参考 Adapter
