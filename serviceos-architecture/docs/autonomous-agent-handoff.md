---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR：https://github.com/hctwgl/serviceos/pull/147
- latestMilestone：**M307**
- Flyway：**118 / 120**；OpenAPI：**1.0.40**
- 下一刀：**M308** RULE 运行时

## 已完成

P0 + M297～M302 Connector SPI；M303～M307 配置运行时（INTEGRATION/ASSIGNEE/DISPATCH/NOTIFICATION）

## BLOCKED_EXTERNAL

Swift/Xcode、签名真机、吉利 Sandbox、远端 verify.yml

## 下一步入口

- RULE schema → 条件求值 / 动作解释 / 失败关闭 / 可审计
- 不写领域副作用，只输出决策与 explanations
