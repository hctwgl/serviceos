---
title: M253 多端客户端元数据与可观测性
status: Implemented
milestone: M253
lastUpdated: 2026-07-18
relatedMilestones: [M250, M251, M252]
---

# M253 多端客户端元数据与可观测性

## 范围与证据

- Core OpenAPI 1.0.21 登记 `X-ServiceOS-Client-Kind` / `X-ServiceOS-Client-Version` 机器约定；
- clientKind 为低基数集合 `ADMIN_WEB`、`NETWORK_WEB`、`TECHNICIAN_WEB`、`TECHNICIAN_IOS`，版本为受限语义版本；
- Web Core 与 iOS Core 请求构造器强制新客户端携带合法元数据，并由各自 strict build/runtime smoke 证明；
- Servlet 入口在认证前规范化元数据，将合法值写入请求属性、MDC 和 OTel Span；缺失、组合不完整或非法输入整体降为
  `UNKNOWN/UNSPECIFIED`，原始不可信值不进入日志或 Trace；
- Header 明确只用于版本兼容诊断与观测，不参与身份、Capability、Scope、菜单或动作授权；
- OpenAPI compatibility、TypeScript/Swift Client 可复现和独立消费门禁通过；Flyway 仍为 100/102。

## 兼容策略

服务端暂不因旧客户端缺少 Header 而拒绝请求，以便后续独立应用双运行迁移；新 Web/iOS Core 构造器已经强制提供。
何时把 Header 升级为服务端 required 必须基于已部署客户端矩阵另行接受，不能在本里程碑猜测。

## 明确未实现

支持能力协商、最低/最高客户端版本策略、强制升级、灰度 cohort、版本指标面板、独立 App 实际接入、制品发布、
设备型号/系统版本采集，以及任何基于客户端元数据的授权分支。
