---
title: Admin 试点可运行基线（含 M163 外部审核回执详情）
status: Implemented
lastUpdated: 2026-07-17
---

# Admin 试点可运行基线（含 M163 外部审核回执详情）

本基线覆盖 M101～M163 已有 Admin 表面的可重复构建、登录、真实后端/数据库试点入口，并明确
平台级未实现边界。M135～M163 追加补传复审、预约上门、提审外发 ACK、厂端回调、入站接单激活、
Admin HTTP 人工初派、同单预约上门→表单/资料/驳回整改补传复审/外发/完结（`ADMIN-PILOT-09`）、
入站 Envelope/Canonical 详情深链、专项队列与目录/SLA Accepted OpenAPI 筛选，以及工作区外发/审核/整改详情深链。

## 1. 已建立的基线

| 项目 | 工程证据 |
|---|---|
| 主门禁 | `bash scripts/verify-local.sh clean verify` 通过；CI 敏感输出扫描不再把 OpenAPI 类型名误判为 VIN |
| Admin CI | GitHub Actions 使用 Node 22 执行不可变安装与生产构建；独立 `admin-pilot-e2e` job 安装 Chrome 并运行真实写链路，staging 等待 Java、Admin build 与 Admin E2E 三个门禁 |
| 本地身份 | Vite 开发模式显式开启 Keycloak Authorization Code + PKCE；无 client secret、无硬编码 token、无生产手工 JWT 入口 |
| 后端授权 | JWT 只提供身份声明；ServiceOS 继续从数据库 RoleGrant 实时校验 tenant/project/capability，401 清理本机会话并失败关闭 |
| 真实 E2E | 固定可释放夹具 + 每轮新建终态/整改豁免/重开/正常补传/现场履约夹具 + Playwright/Google Chrome；覆盖表单资料审核完结、WAIVE、reopen、补传复审，以及 propose→confirm→check-in→check-out |
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
独立整改夹具使用相同真实上传/校验/Snapshot 链路作出普通 REJECTED 决定，经授权
`status=IN_PROGRESS` 队列定位 CorrectionCase，并在详情页执行需要
`evidence.waiveCorrection` 的豁免。豁免后重新读取权威详情，保持 WAIVED、来源 Task、
整改 Task 和成功提示同时可见；同事务取消整改 Task，不用 SQL 模拟终态。
独立重开夹具在 OPEN Case 上执行 `evidence.forceApprove`，保留显式 FORCE_APPROVED 决定且不创建
CorrectionCase；随后执行 `review.reopen`，将原 Case 标记 REOPENED，并创建同 Snapshot 的后继
OPEN Case。Admin 路由切换到后继 Case，展示重开来源与 triggerRef，刷新后身份仍一致。
重开审计使用稳定结果码 `REOPENED`；自由文本原因与 approvalRef 不再写入 40 字符
`result_code`，避免合法长文本导致事务回滚。
M53 表单重解析复用既有 Slot 时，Snapshot 冻结最新 `currentResolutionId`，与 M43 完成门禁保持
同一配置事实。脚本检查 StoredFile AVAILABLE、EvidenceRevision VALIDATED、Snapshot 成员、
精确双引用、ReviewCase APPROVED、唯一 ReviewDecision、创建/裁决审计与审核事件 Inbox、
`task.completed` Inbox 成功消费、候选/责任 EXPIRED、Node/Stage/Workflow COMPLETED 与
WorkOrder FULFILLED；整改夹具还检查 ReviewCase REJECTED、CorrectionCase WAIVED、整改 Task
CANCELLED、三类成功审计与四条审核/整改事件 Inbox；重开夹具检查 FORCE_APPROVED 决定、
原/后继 Case 血缘、三类成功审计、三条审核事件 Inbox 且无 CorrectionCase；正常补传夹具检查
CorrectionCase CLOSED、补传轮次、复审 APPROVED、源 Task COMPLETED、WorkOrder FULFILLED
以及补传/关闭/复审/完结审计与 Inbox。夹具只允许本地开发数据库使用。
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
- Admin 对独立动态 Task 创建 Snapshot 并作出普通 REJECTED 裁决；系统同事务创建
  IN_PROGRESS CorrectionCase 与 `evidence.correction` Task。Admin 经授权队列/详情执行
  CRITICAL WAIVED，Case 与整改 Task 分别进入 WAIVED/CANCELLED，审计和 Outbox/Inbox 完整。
- Admin 对另一动态 Task 作出 FORCE_APPROVED，再重开为同 Snapshot 的后继 OPEN Case；
  原 Case、决定、重开来源和 triggerRef 均保留，页面 URL/刷新与后继身份一致。

已追加证明（M135）：

- Admin 对独立动态 Task 作出普通 REJECTED 后，在源 Task 同 Item 追加补传 Revision 并创建新
  Snapshot；经授权整改详情 `resubmit`→`close`，再对补传 Snapshot 新建 INTERNAL ReviewCase
  并普通 APPROVED；随后以 FormSubmission + 补传 Snapshot 双引用 complete，Outbox/Inbox 推进
  至 WorkOrder FULFILLED。不使用 WAIVE 或同 Snapshot reopen 冒充正常补传复审。

已追加证明（M136）：

- 本地 RoleGrant 具备预约/上门写能力；第五套夹具注入 ACTIVE ServiceAssignment；
- Admin 对独立动态 Task 执行 proposeAppointment→confirm→check-in→check-out，Appointment 与
  Visit 终态均为 COMPLETED，审计与 Outbox/Inbox 完整。

已追加证明（M137）：

- 获权 USER 可创建 BYD 提审交付；夹具登记 CREATE_WORK_ORDER Canonical 系谱；
- 本地协议 stub 严格 `errno=0` 后 Delivery ACKNOWLEDGED 并自动创建 CLIENT ReviewCase；
- Admin 外发详情可见 ACKNOWLEDGED。不宣称真实 sandbox。

已追加证明（M138）：

- 外发详情展示 CLIENT 案例链接；同租户 CPIM 签名回调（result=1）将 CLIENT Case 推进到 APPROVED；
- Admin 浏览器可见 `CLIENT:APPROVED`，并有 EXTERNAL receipt/decision 持久化证据。

已追加证明（M139）：

- 冒烟 Backend 绑定 `SERVICEOS_BYD_CPIM_PROJECT_CODE=ADMIN-PILOT`；
- CPIM 签名 POST `/install-orders` 创建 RECEIVED 工单；
- Admin 目录/工作区/INTEGRATION 可见 CREATE_WORK_ORDER COMPLETED；
- SQL 断言 Envelope/Canonical/审计/Outbox。

已追加证明（M140）：

- ADMIN-PILOT WORKFLOW 可解析；入站后 Outbox 自动 ACTIVE + HUMAN Task；
- 同单 Admin assign/claim/start 与 propose→confirm→check-in→check-out；
- Visit 所需 ServiceAssignment 仍为本地夹具，不宣称 Admin 派单 HTTP。

已追加证明（M141）：

- 入站 Canonical `BYD:INSTALL:{orderCode}` 与出站提审系谱对齐；
- ADMIN-PILOT Bundle 含 formRef + PILOT_SURVEY FORM/EVIDENCE；
- 同单表单/资料/INTERNAL APPROVED/BYD ACK/厂端回调/双输入 complete→FULFILLED；
- Visit 所需 ServiceAssignment 仍为本地夹具。

已追加证明（M142）：

- 同一入站工单 REJECTED → CorrectionCase → 同 Item 补传 → resubmit/close → 复审 APPROVED
  → BYD ACK → 厂端回调 → FULFILLED。

已追加证明（M143）：

- field-ops 与入站工单的 Visit 所需 ServiceAssignment 曾经 Capacity/Assignment SPI 编排注入；
  M144 起改为 Admin HTTP，SPI 种子已删除。

已追加证明（M144 / ADMIN-PILOT-09）：

- Admin HTTP `service-assignments:manual-assign` 人工初派 NETWORK+TECHNICIAN；
- 入站同单：接单→Admin 派单→预约上门→表单/资料→驳回整改补传复审→外发→完结；
- 派单为窄化 Manual Assign（无评分/硬过滤/ServiceNetwork 生命周期）。

已追加证明（M145）：

- 工作区 INTEGRATION 深链打开入站 Envelope/Canonical 安全摘要详情；
- 专用入站队列列表仍未证明。

已追加证明（M146 / ADMIN-PILOT-08OQ）：

- Outbound Delivery 队列 UI 绑定 Accepted OpenAPI 单值筛选（默认 `UNKNOWN`）；
- ACK 后按 `ACKNOWLEDGED` 筛选可见目标交付；多 status OR / SavedView 仍未证明。

已追加证明（M147 / ADMIN-PILOT-08OD）：

- 工作区 INTEGRATION 深链打开外发交付详情（复用已有详情页与 GET）；
- 专用入站队列列表仍未证明。

已追加证明（M148 / ADMIN-PILOT-08RQ / 08CQ）：

- Review/Correction 队列 UI 绑定 Accepted OpenAPI 单值筛选；
- Playwright 证明 `OPEN+taskId` 与 `IN_PROGRESS+sourceReviewCaseId`；多 status OR / SavedView 仍未证明。

已追加证明（M149 / ADMIN-PILOT-08RD）：

- 工作区 REVIEWS_CORRECTIONS 深链打开审核与整改详情；
- 专用入站队列列表仍未证明。

已追加证明（M150 / ADMIN-PILOT-08EQ）：

- Operational Exception 队列 UI 绑定 Accepted OpenAPI 单值筛选（默认 OPEN）；
- Playwright 证明 `ACKNOWLEDGED+P1` 查询 200；多 status OR / SavedView 仍未证明。

已追加证明（M151 / ADMIN-PILOT-08DF）：

- 工单/任务/SLA `projectId`、任务 `SUCCEEDED`、项目 `activeOn` 筛选接到 Admin；
- 专用入站队列列表仍未证明。

已追加证明（M152 / ADMIN-PILOT-08TD）：

- 工作区 TASKS 深链打开任务详情；
- TIMELINE/预约/表单证据独立详情深链、专用入站队列列表仍未证明。

已追加证明（M153 / ADMIN-PILOT-08TL）：

- 工作区 TIMELINE_AUDIT 白名单资源深链打开任务详情；
- Appointment/Visit/Form/Evidence 独立详情页、专用入站队列列表仍未证明。

已追加证明（M154 / ADMIN-PILOT-08AF）：

- 工作区 APPOINTMENTS_VISITS / FORMS_EVIDENCE Task 旁路深链；
- Appointment/Visit/Form/Evidence 独立详情页、专用入站队列列表仍未证明。

已追加证明（M155 / ADMIN-PILOT-08AD）：

- 预约/表单提交只读详情页与工作区深链；
- Visit/EvidenceItem 独立详情页、专用入站队列列表仍未证明。

已追加证明（M156 / ADMIN-PILOT-08ED）：

- 资料项/资料快照只读详情页与工作区/Task 面板深链；
- Visit 独立详情页、专用入站队列列表仍未证明。

已追加证明（M157 / ADMIN-PILOT-08XN）：

- 工作区项目与 SLA 任务交叉深链；
- Admin R1 只读胶水在已 Accepted 契约上已基本收口；Visit/SavedView 仍依赖契约接受。

已追加证明（M158 / ADMIN-PILOT-08IQ）：

- 授权入站 Envelope 队列（API-06 §6.1）与 Admin 筛选/详情深链；
- null-project 可见性、原文下载、SavedView 仍未证明。

已追加证明（M159 / ADMIN-PILOT-08VD）：

- `GET /visits/{id}` 与 Admin 上门详情页；工作区 AV 深链；
- ContactAttempt/FieldOperation 详情、SavedView 仍未证明。

已追加证明（M160 / ADMIN-PILOT-08CA）：

- `GET /contact-attempts/{id}` 与 Admin 联系详情页；工作区 AV 深链；
- FieldOperation 详情、SavedView 仍未证明。

已追加证明（M161 / ADMIN-PILOT-08CT）：

- 权威核心时间线 → FormSubmission / EvidenceSetSnapshot 等已有详情页深链；
- FieldOperation 详情、SavedView 仍未证明。

已追加证明（M162 / ADMIN-PILOT-08AS）：

- 最近活动摘要 → 已有详情页深链（与时间线白名单同构）；
- FieldOperation 详情、SavedView 仍未证明。

已追加证明（M163 / ADMIN-PILOT-08ER）：

- ExternalReviewReceipt Admin 详情与时间线白名单；厂端回调后核心时间线深链；
- FieldOperation 详情、SavedView 仍未证明。

尚未证明：

- 正式企业 IdP、MFA、生产回调地址、BFF/token renewal/logout 协议；
- 评分/硬过滤/DispatchDecision/ServiceNetwork 目录生命周期；
- Network/Technician Portal 与跨端协作；
- 正式 sandbox、对象存储、专业扫描服务、Broker、通知和 SLA BUSINESS 日历；
- 真实 sandbox 提审与生产厂端联调；
- SavedView、设计系统、可访问性与多浏览器矩阵。

因此当前交付可称为“Admin 试点可运行基线（含 ADMIN-PILOT-09、详情深链与专项队列/目录筛选）”，
不能称为“完整现场履约平台已交付”。
