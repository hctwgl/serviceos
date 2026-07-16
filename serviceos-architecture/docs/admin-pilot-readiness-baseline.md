---
title: M134 Admin 试点可运行基线
status: Implemented
lastUpdated: 2026-07-16
---

# M134 Admin 试点可运行基线

本基线不创建 M135/M136，也不扩大 M134 的业务声明范围。目标是让 M101～M134 已有 Admin
表面具备可重复构建、可登录、可连接真实后端和数据库的试点入口，并明确完整业务链尚未证明的边界。

## 1. 已建立的基线

| 项目 | 工程证据 |
|---|---|
| 主门禁 | `bash scripts/verify-local.sh clean verify` 通过；CI 敏感输出扫描不再把 OpenAPI 类型名误判为 VIN |
| Admin CI | GitHub Actions 使用 Node 22 执行不可变安装与生产构建；独立 `admin-pilot-e2e` job 安装 Chrome 并运行真实写链路，staging 等待 Java、Admin build 与 Admin E2E 三个门禁 |
| 本地身份 | Vite 开发模式显式开启 Keycloak Authorization Code + PKCE；无 client secret、无硬编码 token、无生产手工 JWT 入口 |
| 后端授权 | JWT 只提供身份声明；ServiceOS 继续从数据库 RoleGrant 实时校验 tenant/project/capability，401 清理本机会话并失败关闭 |
| 真实 E2E | 固定幂等夹具 + Playwright/Google Chrome 验证登录、工单目录、工作区、详情、Stage、Task、SLA、时间线，以及 Task claim/release |
| 数据库 | 使用 `serviceos-deploy/compose.yaml` 的 PostgreSQL 18，后端启动时执行当前 86 个 Flyway 迁移 |

## 2. P0 根因与修复

远端 Java job 的失败步骤同时执行 Maven `clean verify` 与运行日志敏感输出扫描。本地用同等命令复现后，
Maven 全量验证通过，但 VIN 正则会在 OpenAPI Generator 输出路径中命中
`CompleteHumanTaskRequest` 等 17 字符标识片段，导致安全门禁误报。

修复保持 fail-closed：

- 非结构化 17 位 VIN 仍要求至少包含数字；
- 带 `vin` / `vehicleIdentificationNumber` 字段名的 17 位值继续无条件拦截；
- 增加安全代码类型名、手机号泄露和 VIN 泄露正负样本；
- 使用完整 Maven 日志再次验证敏感输出门禁通过。

## 3. 可重复执行

```bash
npm --prefix serviceos-admin-web ci --no-audit --no-fund
npm --prefix serviceos-admin-web run build

serviceos-deploy/observability/test-sensitive-output-gate.sh
serviceos-deploy/observability/verify-sensitive-output.sh /tmp/serviceos-m134-runtime.log

serviceos-deploy/admin-pilot/verify-admin-smoke.sh
```

E2E 脚本不执行 `down -v`，不会删除开发者的 PostgreSQL 数据卷。夹具使用固定 UUID 和
`ON CONFLICT DO NOTHING`，可重复执行；并在浏览器步骤后检查 Task 回到 READY、ACTIVE 候选唯一、
ACTIVE RESPONSIBLE 清零和成功审计记录存在。夹具只允许本地开发数据库使用。
GitHub Actions 使用同一脚本阻断 PR，并保留 Backend、Admin 与 Playwright 诊断产物；该 job
通过后才启动容器化 staging 发布、回滚和恢复演练。

## 4. 已证明与未证明边界

已证明：

- 浏览器到 Keycloak 的真实授权码 + PKCE；
- Backend 对真实 JWT 的 issuer、JWK、audience 校验；
- 数据库 RoleGrant 的 tenant/capability/project scope；
- Admin 对真实工单只读权威投影的聚合展示。
- Admin 按服务端 allowed-actions 执行 Task claim/release，并由 PostgreSQL 候选/责任事实、
  `If-Match` 与幂等键保护；release 后回到 READY，可重复验证。

尚未证明：

- 正式企业 IdP、MFA、生产回调地址、BFF/token renewal/logout 协议；
- 从外部接单或项目创建开始，经派单、预约、上门、表单、资料、审核、整改、外发到完结的完整写链路；
- Network/Technician Portal 与跨端协作；
- 正式 sandbox、对象存储、扫描服务、Broker、通知和 SLA BUSINESS 日历；
- SavedView、设计系统、可访问性与多浏览器矩阵。

因此本次交付只能称为“Admin 试点可运行局部读写基线”，不能称为“完整现场履约平台已交付”。
