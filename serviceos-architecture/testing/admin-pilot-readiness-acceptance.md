---
title: Admin 试点可运行基线验收（含 M164）
status: Implemented
lastUpdated: 2026-07-17
---

# Admin 试点可运行基线验收（含 M164）

| ID | 场景 | 证据 | 结果 |
|---|---|---|---|
| ADMIN-PILOT-01 | Java/契约/Flyway 干净全量验证 | `bash scripts/verify-local.sh clean verify` | PASS |
| ADMIN-PILOT-02 | CI 同等日志敏感输出扫描 | 完整 Maven 日志 + `verify-sensitive-output.sh` | PASS |
| ADMIN-PILOT-03 | 敏感输出门禁正负样本 | 安全类型名通过；手机号/VIN 泄露被拒绝 | PASS |
| ADMIN-PILOT-04 | Admin 不可变依赖与生产构建 | `npm ci` + `npm run build` | PASS |
| ADMIN-PILOT-05 | 本地 OIDC 失败关闭 | 仅 DEV + 显式 env 开启；生产无 JWT 粘贴入口 | PASS |
| ADMIN-PILOT-06 | 真实登录 | Keycloak Authorization Code + PKCE + audience JWT | PASS |
| ADMIN-PILOT-07 | 真实授权 | Backend + PostgreSQL RoleGrant/Project Scope | PASS |
| ADMIN-PILOT-08 | 真实只读业务路径 | 目录 → 工作区 → 详情/Stage/Task/SLA/时间线 | PASS |
| ADMIN-PILOT-08A | 真实候选分配写路径 | 无 ACTIVE 候选 → Admin MANUAL assign-candidates → 最新批次/ACTIVE 候选/审计成立 | PASS |
| ADMIN-PILOT-08W | 真实 Task 写路径 | allowed-actions → claim → RESPONSIBLE → release → READY；真实 JWT、RoleGrant、候选责任、If-Match 与幂等键 | PASS |
| ADMIN-PILOT-08T | 真实 Task 终态推进 | 每轮新建 Workflow-backed HUMAN Task → assign/claim/start/complete → Inbox 消费 → Node/Stage/Workflow COMPLETED → WorkOrder FULFILLED | PASS |
| ADMIN-PILOT-08F | 真实表单版本引用 | RUNNING Task 锁定 FormVersion → `form.submit` → VALIDATED FormSubmission → 页面保持精确 ref/digest 为双输入完成的主引用 | PASS |
| ADMIN-PILOT-08E | 真实资料双输入完成引用 | `task.created` Outbox/Inbox 解析 Slot → Begin → 同源私有 PUT → Finalize → 本地扫描 → 机器校验 VALIDATED → Snapshot → 页面自动提交 FormSubmission + EvidenceSetSnapshot 精确双引用 → complete | PASS |
| ADMIN-PILOT-08R | 真实资料审核通过 | Snapshot → createReviewCase → 独立审核页读取 OPEN → 普通 APPROVED decide → 权威详情/唯一决定历史 → 创建与裁决审计及两条事件 Inbox 成功 | PASS |
| ADMIN-PILOT-08C | 真实驳回与整改豁免 | 独立 Snapshot → 普通 REJECTED → 自动 IN_PROGRESS CorrectionCase/整改 Task → 授权队列/详情 → CRITICAL WAIVED → 整改 Task CANCELLED；三类审计与四条审核/整改事件 Inbox 成功 | PASS |
| ADMIN-PILOT-08X | 真实强制通过与重开 | 独立 OPEN Case → CRITICAL FORCE_APPROVED → 原 Case REOPENED + 同 Snapshot 后继 OPEN；页面导航/刷新保持后继身份，三类审计与三条事件 Inbox 成功，无 CorrectionCase | PASS |
| ADMIN-PILOT-08S | 真实正常补传关闭复审完结 | 独立 Task：REJECTED → 源 Task 补传 Snapshot → resubmit → close → 新 ReviewCase APPROVED → 双引用 complete → FULFILLED；审计与 Inbox 成功 | PASS |
| ADMIN-PILOT-08V | 真实预约上门写路径 | 独立 Task：propose→confirm→check-in→check-out；Appointment/Visit COMPLETED；审计与 Inbox 成功 | PASS |
| ADMIN-PILOT-08O | 真实 BYD 提审外发 ACK | APPROVED → createBydReviewSubmission → 本地 stub errno=0 → ACKNOWLEDGED + CLIENT Case | PASS |
| ADMIN-PILOT-08CB | 真实厂端回调关闭 CLIENT | CPIM 签名回调 result=1 → EXTERNAL APPROVED；Admin 可见 CLIENT:APPROVED | PASS |
| ADMIN-PILOT-08IN | 真实入站 CREATE_WORK_ORDER 接单 | CPIM 签名 install-orders → Envelope/Canonical；Admin INTEGRATION 可见 | PASS |
| ADMIN-PILOT-08ACT | 真实入站激活与同单预约上门 | 可解析 WORKFLOW → ACTIVE + HUMAN Task；assign/claim/start + propose→confirm→check-in→check-out | PASS |
| ADMIN-PILOT-08FUL | 真实入站同单表单/资料/审核/外发完结 | BYD:INSTALL 系谱；form→snapshot→INTERNAL APPROVED→BYD ACK→厂端回调→dual complete→FULFILLED | PASS |
| ADMIN-PILOT-08COR | 真实入站同单整改补传复审外发完结 | REJECTED→CorrectionCase→同 Item 补传→resubmit/close→复审 APPROVED→BYD ACK→厂端回调→FULFILLED | PASS |
| ADMIN-PILOT-08SA | 真实两阶段服务责任分配 | M453 `manual-assign-network` → Network Portal `assign-technician` → ACTIVE NETWORK/TECHNICIAN；平台不能越过网点直接派师傅 | PASS |
| ADMIN-PILOT-08ID | 真实入站 Envelope/Canonical 详情深链 | INTEGRATION → `/integration/inbound/{id}`；GET Envelope+Canonical；`BYD:INSTALL:` | PASS |
| ADMIN-PILOT-08OQ | 真实外发队列 Accepted OpenAPI 筛选 | 默认 UNKNOWN；ACK 后 `status=ACKNOWLEDGED` 可见 externalOrderCode | PASS |
| ADMIN-PILOT-08OD | 真实工作区外发交付详情深链 | INTEGRATION → `/integration/outbound/{id}`；GET 详情；可见 externalOrderCode | PASS |
| ADMIN-PILOT-08RQ | 真实审核队列 Accepted OpenAPI 筛选 | 默认 OPEN；`OPEN+taskId` 可见目标审核案例 | PASS |
| ADMIN-PILOT-08CQ | 真实整改队列 Accepted OpenAPI 筛选 | 默认 IN_PROGRESS；`IN_PROGRESS+sourceReviewCaseId` 可见目标整改案例 | PASS |
| ADMIN-PILOT-08RD | 真实工作区审核/整改详情深链 | REVIEWS_CORRECTIONS → `/reviews/{id}` 与 `/corrections/{id}` | PASS |
| ADMIN-PILOT-08EQ | 真实运营异常队列 Accepted OpenAPI 筛选 | 默认 OPEN；`ACKNOWLEDGED+P1` 查询 200 | PASS |
| ADMIN-PILOT-08DF | 真实目录/SLA Accepted OpenAPI 筛选补齐 | WO/Task/SLA `projectId`；Task `SUCCEEDED`；Project `activeOn` | PASS |
| ADMIN-PILOT-08TD | 真实工作区 TASKS → 任务详情深链 | TASKS → `/tasks/{taskId}`；GET 详情 200 | PASS |
| ADMIN-PILOT-08TL | 真实工作区 TIMELINE_AUDIT → 资源详情深链 | TIMELINE_AUDIT → 白名单资源详情；Task GET 200 | PASS |
| ADMIN-PILOT-08AF | 真实工作区预约上门/表单资料 → Task 旁路 | AV/FE → `/tasks/{taskId}`；预约确认与完结后 GET 200 | PASS |
| ADMIN-PILOT-08AD | 真实预约/表单提交详情页 | `/appointments/{id}`、`/form-submissions/{id}`；工作区深链 GET 200 | PASS |
| ADMIN-PILOT-08ED | 真实资料项/资料快照详情页 | `/evidence-items/{id}`、`/evidence-set-snapshots/{id}`；工作区/Task 面板深链 GET 200 | PASS |
| ADMIN-PILOT-08XN | 真实工作区项目与 SLA 任务交叉深链 | 项目详情 GET；工作区/SLA 队列 → Task GET 200 | PASS |
| ADMIN-PILOT-08IQ | 真实入站 Envelope 授权队列 | 默认 RECEIVED；COMPLETED+projectId 筛选；详情 GET 200 | PASS |
| ADMIN-PILOT-08VD | 真实上门 Visit 详情页 | `/visits/{id}`；工作区 AV 深链 GET 200 | PASS |
| ADMIN-PILOT-08CA | 真实联系尝试 ContactAttempt 详情页 | `/contact-attempts/{id}`；工作区 AV 深链 GET 200 | PASS |
| ADMIN-PILOT-08CT | 真实核心时间线资源详情深链 | 核心时间线 → FormSubmission / EvidenceSetSnapshot GET 200 | PASS |
| ADMIN-PILOT-08AS | 真实最近活动资源详情深链 | activity-summary → Task GET 200 | PASS |
| ADMIN-PILOT-08ER | 真实外部审核回执详情页 | `/external-review-receipts/{id}`；回调后核心时间线 GET 200 | PASS |
| ADMIN-PILOT-08RC | 真实审核/整改详情交叉深链 | Review→Snapshot、Correction→Snapshot、后继→源 Review GET 200 | PASS |
| ADMIN-PILOT-08EH | 真实工作区异常摘要 → 异常队列水合 | exceptionSummary → `/exceptions?workOrderId&status=OPEN`；表单水合；GET 200 | PASS |
| ADMIN-PILOT-08RW | 真实工作区审核/整改关联资源深链 | REVIEWS_CORRECTIONS → Snapshot / 源审核 GET 200 | PASS |
| ADMIN-PILOT-08TP | 真实 Task 面板资源详情深链 | Contact/Appointment/Visit/FormSubmission/EvidenceItem GET 200 | PASS |
| ADMIN-PILOT-08CM | 真实 Canonical Message 独立详情页 | Envelope/INTEGRATION → `/integration/canonical/{id}` GET 200 | PASS |
| ADMIN-PILOT-08QH | 真实专项队列 route.query 水合 | Review/Correction/Inbound/Outbound 深链筛选表单水合；GET 200 | PASS |
| ADMIN-PILOT-08DH | 真实目录页 route.query 水合 | WorkOrder/Task/SLA/Project 深链筛选表单水合；GET 200 | PASS |
| ADMIN-PILOT-08OX | 真实外发关联资源与回执入站交叉深链 | 源任务/源快照/入站 Envelope GET 200 | PASS |
| ADMIN-PILOT-08PJ | 真实详情页明文 projectId 深链 | 审核详情 → `/projects/{id}` GET 200 | PASS |
| ADMIN-PILOT-08OI | 真实详情页源资源明文字段深链 | 外发事实格 sourceTaskId → Task GET 200 | PASS |
| ADMIN-PILOT-08FI | 真实现场/表单/SLA 事实格 scope 深链 | 预约事实格 taskId → Task GET 200 | PASS |
| ADMIN-PILOT-08HT | 真实运营异常 handlingTaskId 深链 | 异常详情 → 人工接管 Task GET 200 | PASS |
| ADMIN-PILOT-08QC | 真实专项队列关联资源深链 | 整改队列 → 源审核 / 整改 Task GET 200 | PASS |
| ADMIN-PILOT-08QO | 真实外发/异常/入站队列源资源深链 | 外发源任务 / 异常接管 Task / 入站项目 GET 200 | PASS |
| ADMIN-PILOT-08DP | 真实目录/SLA 项目关联深链 | 工单/任务目录 → Project GET 200 | PASS |
| ADMIN-PILOT-08PP | 真实剩余详情页 projectId 深链 | 整改详情 → `/projects/{id}` GET 200 | PASS |
| ADMIN-PILOT-08QR | 真实专项队列剩余关联资源深链 | 审核队列 Snapshot / 整改队列 Project·来源 Task GET 200 | PASS |
| ADMIN-PILOT-08QL | 真实 QueueTable 行内单元格深链 | 审核队列表格 projectId → Project GET 200 | PASS |
| ADMIN-PILOT-08EM | 真实外发执行任务与快照成员深链 | 外发 executionTask / Snapshot member EvidenceItem GET 200 | PASS |
| ADMIN-PILOT-08CI | 真实写链路 CI 阻断 | GitHub Actions `admin-pilot-e2e` 运行同一 OIDC/Backend/PostgreSQL/Chrome smoke；通过后才启动 staging | PASS |
| ADMIN-PILOT-09 | 完整履约写链路 | 接单→Admin 派单(HTTP Manual Assign)→预约→上门→表单/资料→审核/整改→外发→完结 | PASS |

`ADMIN-PILOT-08*` 与 `ADMIN-PILOT-08CI` 证明固定工单候选分配/领取/释放，独立 Workflow Task 的
表单/资料/审核/整改/完结与外发 ACK/厂端回调，入站工单经 Admin HTTP 初派后同单预约上门→表单/
资料/驳回整改补传复审/外发/完结，入站/外发/审核/整改/TASKS/TIMELINE 资源详情深链、预约上门/表单资料
Task 旁路与预约/表单提交/资料项/资料快照/上门/联系详情页、工作区项目与 SLA 任务交叉深链、详情页
明文 projectId / 源资源 / 现场与 SLA scope 字段深链（含剩余详情页 projectId）、运营异常 handlingTaskId 深链、专项队列关联资源深链（含外发/异常/入站源资源、目录/SLA 项目与 Correction/Review 剩余 Accepted 字段）、QueueTable 行内单元格深链、外发 executionTaskId / 快照成员资料项深链，以及专项队列与目录/SLA Accepted OpenAPI 筛选。`ADMIN-PILOT-09` 已由入站路径证明；派单为
窄化 Manual Assign。不证明 FieldOperation 详情、SavedView、评分/硬过滤引擎、ServiceNetwork
生命周期或生产对象存储/专业扫描。
