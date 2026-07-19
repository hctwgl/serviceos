---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR：https://github.com/hctwgl/serviceos/pull/147
- latestMilestone：**M304**
- Flyway：**118 / 120**；OpenAPI：**1.0.40**
- 下一刀：**M305** ASSIGNEE_POLICY 运行时

## 已完成

P0 + M297～M302 Connector SPI；M303 Mapping 运行时；**M304** 建单主路径 Mapping 闸门

## BLOCKED_EXTERNAL

Swift/Xcode、签名真机、吉利 Sandbox、远端 verify.yml

## 下一步入口

- ASSIGNEE_POLICY schema → 候选集/优先级/Fallback 解释引擎
- 不得绕过 TaskAssignment / 授权模型
