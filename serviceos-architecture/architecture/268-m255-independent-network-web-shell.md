---
title: M255 独立 Network Web AppShell 与环境基础
status: Implemented
milestone: M255
lastUpdated: 2026-07-18
relatedMilestones: [M194, M254]
---

# M255 独立 Network Web AppShell 与环境基础

## 范围与证据

- 新增独立 `serviceos-network-web` Vue 3 + TypeScript 6 + Vite 8 工程，不以 Admin 子路由作为构建入口；
- 独立 `package.json` / `package-lock.json`、端口 5174、生产 build、响应式 AppShell 和基础可访问语义；
- 环境只承载 `local/development/test/staging/production`、API base 与发布版本，`clientKind` 固定为
  `NETWORK_WEB`，不能由部署变量改写；
- API base 只接受同源绝对路径或 HTTPS；未知环境、生产 HTTP 和非法语义版本失败关闭；
- `agent-verify.sh network-web` 执行不可变 `npm ci`、TypeScript/Vue production build、环境正负探针和 dist 检查；
- 首次真实构建产出 index、CSS 与 JS，门禁通过；Core OpenAPI 仍为 1.0.21，Flyway 仍为 100/102。

## 明确未实现

OIDC、`/me`、Network Context、Capability、服务端导航、路由、缓存、API Client 实际接入、现有 M194～M242
页面迁移、E2E、部署清单和 Admin 旧路由删除。

## 迁移约束

在新应用完成真实会话、数据隔离和双运行 E2E 前，Admin 中现有 Network 路由继续保留；本切片不得被解释为
Network Portal 已可供业务使用。
