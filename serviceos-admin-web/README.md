# ServiceOS Admin Web（M101～M134）

总部运营后台：已覆盖 OpenAPI 中面向 Admin JWT 的已实现运营表面（队列/详情/命令/工单权威投影）。
CI 使用 Node 22 执行不可变依赖安装与生产构建，并通过真实 Keycloak、Backend、PostgreSQL 与
Google Chrome 阻断验证 Task 手工分配候选、领取、释放，以及独立 Task 启动/完成至 WorkOrder
FULFILLED 的局部写链路。

```bash
npm ci
npm run dev
npm run build
```

## 本地 OIDC

复制 `.env.development.example` 为 `.env.development.local`，启动
`serviceos-deploy/compose.yaml` 中的 PostgreSQL/Keycloak 与后端后，可使用
`developer / local-dev-change-me` 走 Authorization Code + PKCE。该适配器仅在显式开启的
Vite 开发模式可用，生产构建失败关闭，不提供手工粘贴 JWT 的后门。

## 真实端到端冒烟

```bash
serviceos-deploy/admin-pilot/verify-admin-smoke.sh
```

脚本复用本机 Google Chrome，验证真实 Keycloak、Backend、PostgreSQL 与 Admin Web：
登录 → 工单目录 → 工作区 → 工单详情/Stage/Task/SLA/核心时间线
→ Task MANUAL 候选分配 → 领取/释放；另以每轮新建的 Workflow-backed HUMAN Task 验证
候选分配 → 领取 → 启动 → 完成 → Node/Stage/Workflow COMPLETED → WorkOrder FULFILLED。
脚本不会删除本地数据卷，并通过真实 RoleGrant、候选快照、责任事实、版本和幂等保护执行命令；
浏览器完成后还会校验 READY、候选/责任事实、成功审计、Outbox/Inbox 消费与终态一致性。
GitHub Actions 的 `admin-pilot-e2e` job 使用同一脚本，成功后才允许进入 staging 发布与回滚演练。

明确未实现：设计系统、SavedView、正式企业 OIDC/BFF、Network/Technician、
SERVICE-only 车企适配层 UI，以及完整履约写链路 E2E。
