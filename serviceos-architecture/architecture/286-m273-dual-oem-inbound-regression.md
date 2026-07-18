---
title: M273 双车企入站端到端回归
status: Implemented
milestone: M273
lastUpdated: 2026-07-18
relatedMilestones: [M56, M267, M272]
---

# M273 双车企入站端到端回归

## 目标

证明 BYD 与 REFERENCE_OEM SAMPLE 使用独立 Connector 与独立 ConfigurationBundle 并行建单；冲突正文失败关闭。

## 已实现

- `DualOemInboundRegressionPostgresIT`：两租户/两 Bundle；独立 connectorVersion；业务键冲突不覆盖。
- REFERENCE 侧仍为 SAMPLE（非真实第二家协议）。

## 明确未实现

真实第二家 Sandbox、提审/回调双车企全链路、乱序回执全矩阵（BYD 侧既有 M58～M60 覆盖）。
