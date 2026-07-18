---
title: M250 Web auth/context/error/trace 共享基础
status: Implemented
milestone: M250
lastUpdated: 2026-07-18
relatedMilestones: [M188, M247, M249]
---

# M250 Web auth/context/error/trace 共享基础

## 范围与证据

- 新增独立 `@serviceos/web-core` TypeScript 6 包，供后续 Network Web 与 Technician H5 共同消费；
- Access Token 仅驻留内存，临近到期即清除；共享包不访问 localStorage/sessionStorage，不实现开发 OIDC；
- Context Store 只保存服务端签发的 opaque `contextId/contextVersion`，边界或版本变化通知宿主清理查询缓存；
- Fetch 客户端统一 Bearer、请求 correlation ID、响应 ETag/correlation/trace 诊断，并允许宿主传入服务端定义的
  context header；不解析或拼装 tenant/project/network 范围；
- Problem Details 保留结构化诊断，401 触发宿主重新认证，最终用户文案固定映射且不直接回显 detail；
- `agent-verify.sh web-core` 完成 TypeScript 严格编译、4 项行为测试、npm pack、临时消费者安装和 ESM 导入；
- 既有 Admin Web production build 通过；Core OpenAPI 1.0.20 与 Flyway 100/102 不变。

## 明确未实现

生产 OIDC/BFF、refresh token、正式 Context API 适配、生成 TypeScript Client 注入、独立 Network/Technician App
消费、iOS 对应基础、clientKind/clientVersion、浏览器 E2E 与远端包发布。

## 安全与失败关闭

共享源码出现 Portal/角色词、Token 非法/到期、空 baseUrl、非 `/` path、401、非 JSON 错误体或上下文版本
变化均按明确路径失败关闭；共享包不授予权限，也不把前端页面可见性当授权。
