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
| 真实 E2E | 固定可释放夹具 + 每轮新建终态夹具 + Playwright/Google Chrome 验证登录、权威只读投影、Task MANUAL assign-candidates/claim/release，以及独立 Task assign/claim/start、锁定表单提交、资料 Begin/PUT/Finalize、本地扫描、机器校验、Snapshot、INTERNAL ReviewCase 普通 APPROVED 裁决、精确双引用 complete 到 WorkOrder FULFILLED |
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

E2E 脚本不执行 `down -v`，不会删除开发者的 PostgreSQL 数据卷。可释放夹具使用固定 UUID 和
`ON CONFLICT DO NOTHING`；每轮先关闭上轮 ACTIVE 候选但不回退 Task version，再由页面创建新的
MANUAL 候选批次。终态夹具每轮创建全新的 WorkOrder/Workflow/Stage/Node/Task UUID，完成后保留
不可变历史，不通过 SQL 回退终态。终态 Task 锁定最小 FormVersion 与 Evidence 模板；
`task.created` 通过真实 Outbox/Inbox 解析 EvidenceSlot。浏览器提交 VALIDATED FormSubmission，
再执行 Evidence Begin、经 Vite 同源代理访问 Backend 签发的私有 PUT、Finalize、本地扫描、
机器校验和 Snapshot；页面保持 FormSubmission 为主引用并自动组合两份精确版本引用。
同一 OIDC 会话在独立审核页创建并读取 INTERNAL ReviewCase，作出普通 APPROVED 决定后重新读取
权威详情，保持成功消息、案例状态和唯一决定历史同时可见。
M53 表单重解析复用既有 Slot 时，Snapshot 冻结最新 `currentResolutionId`，与 M43 完成门禁保持
同一配置事实。脚本检查 StoredFile AVAILABLE、EvidenceRevision VALIDATED、Snapshot 成员、
精确双引用、ReviewCase APPROVED、唯一 ReviewDecision、创建/裁决审计与审核事件 Inbox、
`task.completed` Inbox 成功消费、候选/责任 EXPIRED、Node/Stage/Workflow COMPLETED 与
WorkOrder FULFILLED。夹具只允许本地开发数据库使用。
GitHub Actions 使用同一脚本阻断 PR，并保留 Backend、Admin 与 Playwright 诊断产物；该 job
通过后才启动容器化 staging 发布、回滚和恢复演练。

## 4. 已证明与未证明边界

已证明：

- 浏览器到 Keycloak 的真实授权码 + PKCE；
- Backend 对真实 JWT 的 issuer、JWK、audience 校验；
- 数据库 RoleGrant 的 tenant/capability/project scope；
- Admin 对真实工单只读权威投影的聚合展示。
- Admin 通过真实 MANUAL assign-candidates 创建候选快照，再按服务端 allowed-actions 执行
  Task claim/release；全链路由 PostgreSQL 候选/责任事实、`If-Match` 与幂等键保护，
  release 后回到 READY，可重复验证。
- Admin 对每轮新建的 Workflow-backed HUMAN Task 执行 assign/claim/start，提交精确锁定的
  FormVersion，完成真实资料上传、扫描、机器校验与 Snapshot，并使用 VALIDATED FormSubmission
  和 EvidenceSetSnapshot 创建 INTERNAL ReviewCase、作出普通 APPROVED 裁决，再以精确双引用
  complete；审核与 `task.completed` 事件均经 Outbox/Inbox 可靠消费，最终推进 Node、Stage、
  Workflow，并将独立 WorkOrder 置为 FULFILLED。

尚未证明：

- 正式企业 IdP、MFA、生产回调地址、BFF/token renewal/logout 协议；
- 从外部接单或项目创建开始，经派单、预约、上门、表单、资料、审核、整改、外发到完结的完整写链路；
- Network/Technician Portal 与跨端协作；
- 正式 sandbox、对象存储、专业扫描服务、Broker、通知和 SLA BUSINESS 日历；
- REJECTED 后整改、强制通过/重开、外部提审与回执在同一浏览器写链路中的端到端证明；
- SavedView、设计系统、可访问性与多浏览器矩阵。

因此本次交付只能称为“Admin 试点可运行局部读写基线”，不能称为“完整现场履约平台已交付”。
