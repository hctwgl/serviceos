---
title: M253 多端客户端元数据与可观测性验收矩阵
status: Implemented
milestone: M253
lastUpdated: 2026-07-18
---

# M253 多端客户端元数据与可观测性验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M253-01 | Web 请求 | 合法 kind/version 自动进入每个 Core 请求 | TypeScript strict + Node assertions |
| M253-02 | iOS 请求 | `TECHNICIAN_IOS` 与发布版本自动进入 URLRequest | Swift 6 strict smoke |
| M253-03 | 服务端观测 | 合法值在请求属性和 MDC 中可见，请求后 MDC 清理 | Filter unit test |
| M253-04 | 恶意/缺失输入 | 统一为 `UNKNOWN/UNSPECIFIED`，不保留原文 | negative Filter test |
| M253-05 | 授权隔离 | OpenAPI 明确 `authorizationInput=false` | contract test + source review |
| M253-06 | 契约兼容 | OpenAPI 1.0.21 为非破坏变更，生成客户端仍可复现消费 | oasdiff + TS/Swift client gates |

## 明确未验收

版本兼容策略、强制升级、能力协商、指标面板、独立应用接入、真机/浏览器 E2E 和远端制品。
