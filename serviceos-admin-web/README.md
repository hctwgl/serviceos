# ServiceOS Admin Web（M101～M134）

总部运营后台：已覆盖 OpenAPI 中面向 Admin JWT 的已实现运营表面（队列/详情/命令/工单权威投影）。
CI 使用 Node 22 执行不可变依赖安装与生产构建，并通过真实 Keycloak、Backend、PostgreSQL 与
Google Chrome 阻断验证 Task 手工分配候选、领取、释放，以及独立 Task 启动/完成至 WorkOrder
FULFILLED 的局部写链路；终态 Task 通过锁定 FormVersion 提交 VALIDATED FormSubmission，
再通过资料 Begin/PUT/Finalize、本地扫描、机器校验与 Snapshot 形成不可变 EvidenceSetSnapshot，
创建 INTERNAL ReviewCase 并在独立审核页作出普通 APPROVED 裁决，随后由页面自动组合表单与
资料两份精确版本引用后完成。另一个独立动态工单验证普通 REJECTED 裁决、整改队列/详情、
CRITICAL 豁免与整改 Task 取消。

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
候选分配 → 领取 → 启动 → 锁定表单提交 → 资料上传/校验/Snapshot → 创建审核案例/APPROVED
→ 双输入完成
→ Node/Stage/Workflow COMPLETED
→ WorkOrder FULFILLED；另以独立 Task 验证 Snapshot → REJECTED → 自动 CorrectionCase/
整改 Task → 授权 WAIVED → 整改 Task CANCELLED。
脚本不会删除本地数据卷，并通过真实 RoleGrant、候选快照、责任事实、版本和幂等保护执行命令；
浏览器完成后还会校验 READY、候选/责任事实、表单和资料精确双引用、StoredFile AVAILABLE、
EvidenceRevision VALIDATED、Snapshot 成员、成功审计、Outbox/Inbox 消费与终态一致性。
审核链路还校验唯一 ReviewDecision、创建/裁决审计，以及两条审核事件均被 Inbox 成功消费。
整改链路校验来源审核引用、IN_PROGRESS 队列、整改 Task 深链、豁免审计，以及审核/整改四条事件
均被 Inbox 成功消费；豁免成功后页面继续展示权威 Case 状态与任务引用。
本地文件通过 Vite 同源代理上传，Backend 仍校验短期 token、大小、摘要和 MIME；该证据不代表
生产对象存储或专业扫描服务已经接入。
GitHub Actions 的 `admin-pilot-e2e` job 使用同一脚本，成功后才允许进入 staging 发布与回滚演练。

明确未实现：设计系统、SavedView、正式企业 OIDC/BFF、Network/Technician、
SERVICE-only 车企适配层 UI，以及完整履约写链路 E2E。
