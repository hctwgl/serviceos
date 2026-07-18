---
title: M251 iOS auth/context/error/trace 共享基础
status: Implemented
milestone: M251
lastUpdated: 2026-07-18
relatedMilestones: [M248, M249, M250]
---

# M251 iOS auth/context/error/trace 共享基础

## 范围与证据

- 新增独立 `ServiceOSIOSCore` Swift 6 模块，供后续 Technician iOS 工程消费；
- 定义 `AccessTokenProviding` 协议、显式到期/提前失效语义与仅供测试/未登录启动使用的 Actor 内存 Vault；
- `ContextSelectionStore` 只保存服务端签发的 opaque `contextId/contextVersion`，边界或版本变化时异步通知宿主清理缓存；
- `ServiceRequestBuilder` 强制非本地 HTTPS，统一 Bearer、correlation ID、JSON 头和宿主传入的服务端 Context Header；
- `ProblemDetails`、`ServiceAPIError` 与 `DiagnosticContext` 对齐 M250 Web 基线，保留 error/trace/correlation 诊断，最终用户文案不回显后端 detail；
- `agent-verify.sh ios-core` 使用 Apple Swift 6.4 严格编译模块和独立异步 executable，验证 Token、请求头、Context 版本切换和安全错误文案；
- Core OpenAPI 仍为 `1.0.20`，Flyway 仍为 100/102，无 HTTP、事件或数据库变化。

## 明确未实现

Xcode App、Keychain 生产 Vault、OIDC PKCE/ASWebAuthenticationSession、URLSession transport、证书策略、日志落盘、
clientKind/clientVersion、真机/模拟器测试、后台任务与离线运行时。

## 安全与失败关闭

空 Token、过期/临近到期 Token、空 Context 标识或版本、非本地 HTTP、非 `/` API path 均失败关闭。
内存 Vault 的名称、注释和文档均明确禁止将其作为生产 iOS Token 存储；共享模块不含 Portal、角色、菜单或数据范围判断。
