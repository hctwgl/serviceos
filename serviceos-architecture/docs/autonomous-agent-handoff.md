---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR：https://github.com/hctwgl/serviceos/pull/147
- latestMilestone：**M305**
- Flyway：**118 / 120**；OpenAPI：**1.0.40**
- 下一刀：**M306** DISPATCH 运行时

## 已完成

P0 + M297～M302 Connector SPI；M303/M304 INTEGRATION Mapping；**M305** ASSIGNEE_POLICY 运行时

## BLOCKED_EXTERNAL

Swift/Xcode、签名真机、吉利 Sandbox、远端 verify.yml

## 下一步入口

- DISPATCH schema → 硬过滤/评分/权重/并列/无候选降级/派单解释
- 不得绕过 TaskAssignment 与授权
