---
title: ServiceOS 实施状态总览
version: 0.1.0
status: Implemented
lastUpdated: 2026-07-17
baselineCommit: 63f4936329a7ebcedfbce8a0c50320d76f277349
latestMilestone: M167
---

# ServiceOS 实施状态总览

本文件是 ServiceOS 面向项目负责人、开发者和 Agent 的统一实施进度入口，用于回答：

1. 当前已经实施了什么；
2. 每项能力由哪些代码、迁移、契约和测试证明；
3. 哪些能力只完成了部分纵向切片；
4. 哪些能力仍停留在设计阶段；
5. 下一阶段应从哪里继续。

本文件不替代架构设计、里程碑实现文档和验收矩阵。发生冲突时，以已接受 ADR、机器契约、测试证据和对应里程碑文档为准。

## 1. 状态定义

| 状态 | 含义 |
|---|---|
| `IMPLEMENTED` | 已有代码、数据库迁移、机器契约和适用自动化验收证据 |
| `PARTIAL` | 已完成一个或多个可靠纵向切片，但该业务能力整体尚未闭环 |
| `ACCEPTED` | 设计已经接受，可指导实现，但尚无完整工程证据 |
| `PROPOSED` | 已形成可评审设计，但尚未被接受或实施 |
| `BLOCKED` | 依赖外部业务确认、ADR 或基础设施决定，暂不能可靠实施 |

注意：

- `Accepted` 不等于已经开发；
- `Implemented` 只表示对应里程碑声明的范围已经实现，不代表整个领域完成；
- 判断完成范围时必须同时阅读实现文档中的“明确未实现”和对应验收矩阵。

## 2. 当前基线

| 项目 | 当前值 |
|---|---|
| 最新实施里程碑 | M167 Admin Task 面板资源详情深链 |
| 基线提交 | `63f4936329a7ebcedfbce8a0c50320d76f277349` |
| 后端形态 | Java 21 + Spring Boot + Spring Modulith 模块化单体 |
| 当前可构建工程 | `serviceos-backend`、`serviceos-contracts` |
| 前端工程 | `serviceos-admin-web`（Vue+TS+Vite）已纳入 CI 构建，具备开发态 Keycloak PKCE，以及真实只读、Task MANUAL assign-candidates/claim/release、表单/资料/审核/整改/完结、正常补传复审，预约上门、BYD 提审外发 ACK、厂端回调，CPIM 入站→激活→Admin HTTP 人工初派→同单预约上门→表单/资料/驳回整改补传复审/外发/完结（ADMIN-PILOT-09），入站 Envelope 授权队列与详情深链、专项队列与目录/SLA Accepted OpenAPI 筛选，工作区各按需区块详情或 Task 旁路、预约/表单/资料/上门/联系详情页、核心时间线与最近活动资源深链、外部审核回执详情、审核/整改交叉深链、工作区异常摘要→异常队列 query 水合、工作区审核/整改关联资源深链、Task 面板资源详情深链，以及工作区项目与 SLA 任务交叉深链的 PR 阻断 E2E；Network/Technician 尚未建立 |
| 数据库 | PostgreSQL + Flyway（当前版本 085 / 87） |
| 契约 | Core OpenAPI 0.75.0 + BYD CPIM OpenAPI 0.3.0 + 外部/事件 JSON Schema（含 project.created@v3、project.scope-relations-revised@v1、recovered/resolved 与 SLA started/breached/met@v1） |

每次完成新里程碑时，Agent 必须更新本节的最新里程碑、基线提交和更新时间。

## 3. 能力实施总览

| 领域 | 能力 | 状态 | 已完成范围 | 主要未完成范围 | 最近证据 |
|---|---|---|---|---|---|
| 工程基础 | 构建、测试、契约、可观测性、容器发布 | `IMPLEMENTED` | Maven、PostgreSQL IT、契约门禁、Trace/指标、单镜像迁移和回滚演练 | 正式 K8s、多故障域、PITR、SBOM/签名、正式 Secret Manager | M8～M14 |
| 身份授权 | OIDC/JWT、Capability、Tenant/Project/REGION/NETWORK Scope、拒绝审计 | `IMPLEMENTED` | 后端认证授权和范围校验基线；实时 TENANT/PROJECT/REGION/NETWORK 集合；Project 有效期关系、整组修订与授权目录读取 | 组织关系、Region 层级后代、计划修订/审批、正式企业 IdP、完整组织治理 UI | M9、M63～M67 |
| 项目治理 | Project 核心事实、范围关系与授权目录 | `PARTIAL` | 项目创建；REGION/NETWORK 当前关系整组修订和不可变历史；`project.read` 授权目录、详情及历史查询 | owners、品牌/服务产品/配置绑定、生命周期、计划修订审批、目录治理 UI | M8、M64～M67 |
| 可靠消息 | Inbox、Outbox、Worker claim/lease/retry | `IMPLEMENTED` | 本地可靠发布消费、恢复和人工接管基础 | 正式 Broker 和跨服务运行 | M9～M10 |
| 配置中心 | 不可变配置资产、Bundle 发布和版本锁定 | `PARTIAL` | FORM、EVIDENCE、SLA v1 资产发布基础；工单/任务冻结引用；SERVICEOS_EXPR_V1 布尔/类型比较子集；FORM/EVIDENCE 字段及 WORKFLOW/SLA 依赖闭包 | 决策表/公式/脚本、完整审批和通用依赖图 | M16、M33、M36、M52～M53、M61 |
| 外部接入 | BYD CPIM V7.3.1 入站、提审与审核回调 | `PARTIAL` | 协议日期验签、防重放、私有原文、Envelope/Canonical、工单创建；显式审核路由与逐订单回调；不可变 OutboundDelivery/Attempt/Acknowledgement、Task 可靠执行、UNKNOWN 人工接管与授权人工重发；重发严格 ACK 后发布恢复事实；交付创建/确认/恢复/重发请求与异常确认已并入工单时间线；授权跨项目外发交付队列与入站 Envelope 队列 | 其他 CPIM 消息、人工标记已送达/放弃、通用 Connector、生产凭据/对象存储和真实 sandbox、null-project 入站可见性 | M16、M56～M60、M77～M79、M99、M158 |
| 工单 | WorkOrder 接收、激活、履约完成与授权工作区投影 | `PARTIAL` | 权威工单、工作流启动、跨阶段和 END 完结；授权目录、非 PII 详情、Stage/Task 执行骨架及核心执行+现场履约时间线 | 完整取消、暂停、恢复、客户敏感详情审计、跨域完整时间线/动作与全部业务分支 | M16～M19、M68～M69、M73～M74 |
| 工作流 | 线性 Stage/Task 运行时 | `PARTIAL` | 精确版本启动、线性推进、唯一跨阶段推进、完成事件；节点 `slaRef` 传递；授权 Workflow/Stage 当前投影 | 并行/汇聚网关、流程条件表达式、Node/Attempt 历史和复杂流程语义 | M17～M19、M61、M69 |
| 人工任务与执行历史 | claim/start/complete、责任、执行保护与授权任务读取 | `IMPLEMENTED` | 人工命令、候选领取、唯一责任、release/reclaim、执行保护；表单/资料完成门禁；授权队列/详情、allowed-actions、自动 Attempt 历史及工单内核心 Task 生命周期与指派/Guard/人工接管时间线 | block/retry/cancel 等其他动作、Workflow Node 历史、跨工单/跨域完整历史和 Review 完成条件 | M20～M23、M35、M41、M43、M69～M73、M81 |
| 应用只读投影 | 工作区、队列、时间线和投影运行时 | `PARTIAL` | 独立 readmodel 模块；核心执行、现场履约、SLA、资料/审核/整改（含外部回执与条件 KEEP/INVALIDATE 处置）、外发交付全链路、异常确认/闭环、ServiceAssignment 与 Task 指派/Guard/人工接管 Inbox 投影；授权时间线与稳定分页及最近活动摘要；时间线 checkpoint/dead letter/generation 重建与 FRESH/LAGGING/UNKNOWN/REBUILDING freshness；definition 登记、dead letter 幂等重放与旧/孤儿 generation 清理；工单工作区顶层实时组合、当前 ACTIVE 服务责任摘要与 TASKS/TIMELINE_AUDIT/APPOINTMENTS_VISITS（含联系尝试）/FORMS_EVIDENCE（含提交与资料项安全元数据）/REVIEWS_CORRECTIONS（含 CLIENT/重开血缘）/INTEGRATION 按需区块（敏感字段最小化；缺权次级区块降级）；授权跨项目 ReviewCase/CorrectionCase/OutboundDelivery/InboundEnvelope 专项队列 | 试算合并、revision/slots 技术噪声、表单值与资料版本详情、FACTS_CALCULATIONS、关键事件 taxonomy/过滤、通用 work-queues、SavedView、搜索、多投影平台、Broker offset、Portal、Admin 重建/重放 HTTP | M73～M99、M158 |
| 服务分配 | 网点分配、容量、改派 Saga、超时恢复 | `IMPLEMENTED` | ServiceAssignment、容量权威、改派、终止、对账和自动恢复 | 完整策略评分、全部异常分支和 UI | M24～M28 |
| 运营异常 | 异常工作台基础 | `PARTIAL` | 异常记录和恢复入口；M58 将外发 UNKNOWN 与 Task 最终人工事件汇入 OperationalException + HUMAN Task；M59 提供高风险人工重发事实；M60 在严格 ACK 后幂等闭环对应异常并处理事件乱序；列表/详情/确认已硬化为实时项目范围 | 人工标记已送达/放弃、其他异常类型自动闭环、完整通知、运营中心前端和跨域异常目录 | M29、M58～M60、M100 |
| 预约 | 预约修订、联系终态动作 | `PARTIAL` | Revision、并发和终态动作基础；公开事件已并入工单时间线；Admin propose/confirm E2E；`GET /contact-attempts/{id}` 与详情页 | 用户确认渠道、完整日程和跨端协作 | M30～M31、M74、M136、M160 |
| 现场作业 | Visit 生命周期 | `PARTIAL` | Visit 运行时基础；签到/签退/中断事件已并入工单时间线；Admin check-in/check-out E2E；`GET /visits/{id}` 与详情页 | GPS 策略增强、完整现场提交、离线同步和师傅端 | M32、M74、M136、M159 |
| 动态表单 | 资产、冻结版本、不可变提交和 Task 完成门禁 | `PARTIAL` | 固定/条件 required、visible 与布尔 validation rule，基础类型校验、精确版本提交和完成引用；form.submitted 已并入工单时间线 | 复杂 validator、计算字段、草稿、冲突、更正和审核 | M33～M35、M53、M76 |
| 资料 Evidence | 资产、槽位、Item/Revision、机器校验、Snapshot、完成门禁、作废、Review、Correction | `PARTIAL` | 固定/条件槽位、VALIDATED 表单触发只追加重解析、槽位世代/lineage、REVIEW_REQUIRED 与显式 KEEP/INVALIDATE（处置已并入工单时间线）、安全文件联动、Snapshot/完成门禁及审核整改链路 | OCR/CV、GPS 权威距离、长期归档 | M36～M53、M76、M82～M83 |
| 安全文件 | Begin/Finalize/隔离/扫描/授权下载/作废 | `IMPLEMENTED` | 独立安全文件生命周期；Evidence 编排 Begin/Finalize/Invalidate 联动 | 正式对象存储、专业扫描服务、物理删除 | M11、M38、M46 |
| 审核整改 | ReviewCase、ReviewDecision、CorrectionCase | `PARTIAL` | Review + Correction + 整改 Task + 强制通过/重开 + 车企回执 + WAIVED；CLIENT Case 来源、批次/mapping 冻结；交付明确成功后自动创建 CLIENT Case/Route，UNKNOWN 可授权人工重发并在严格 ACK 后闭环异常；授权跨项目 ReviewCase 与 CorrectionCase 队列 | SLA/assignee enrich、多候选人策略、前端、人工标记已送达/放弃、自动 Evidence target 映射 | M44～M60、M97～M98 |
| SLA | 时钟、预警、升级 | `PARTIAL` | Task `TASK_CREATED→TASK_COMPLETED` ELAPSED 时钟；显式策略版本/摘要锁定；TARGET_DUE 对账；RUNNING/BREACHED/MET/MET_LATE；Inbox/Outbox 与不可变 segment/milestone；`sla.read` + 实时 TENANT/PROJECT/REGION/NETWORK 授权集合的跨项目工作台、工单时间线与详情查询；关系修订使旧游标失败关闭；公开 started/breached/met 已并入工单执行时间线 | BUSINESS 日历、暂停/恢复、免责/重算、预警/升级/通知、其他 subject、组织关系、Portal 前端、考核结算 | M61～M66、M75 |
| 通知 | 通知与运营异常中心 | `PROPOSED` | 已有总体设计 | 通知通道、模板、可靠发送和 UI | `architecture/14-*` |
| 履约事实与试算 | 事实提取和双向试算 | `PROPOSED` | 已有设计、API 和数据规划 | 运行时、投影和前端工作区 | M5 设计 |
| 对账结算 | 对账、结算、争议与调整 | `PROPOSED` | 已有边界设计 | 正式运行时和页面 | `architecture/16-*` |
| Admin Portal | 总部运营后台 | `PARTIAL` | M101～M167：队列/任务/SLA/异常/入站/外发/工单/项目目录、工作区、allowed-actions；CI 阻断构建；开发态 Keycloak PKCE；真实只读与写链路 PR 阻断 E2E（含 ADMIN-PILOT-09、工作区各区块详情或 Task 旁路、预约/表单/资料/上门/联系详情页、核心时间线与最近活动资源深链、外部审核回执详情、审核/整改交叉深链、异常摘要→异常队列 query 水合、工作区审核/整改关联资源深链、Task 面板资源详情深链、项目与 SLA 任务交叉深链、专项队列与目录/SLA Accepted OpenAPI 筛选、入站 Envelope 授权队列） | 设计系统、SavedView、正式企业 OIDC/BFF、生产对象存储/专业扫描、评分/硬过滤派单与 ServiceNetwork 生命周期 | M7 设计、M101～M167、Admin 试点基线 |
| Network Portal | 网点协作端 | `PROPOSED` | 页面和跨端协作规格 | 前端代码和 E2E | M7 设计 |
| Technician App | 师傅移动端 | `PROPOSED` | 弱网、离线工作包、上传队列和页面规格 | 移动端工程、真机和离线运行时 | M7 设计 |
| External Portal | 用户/车企受控页面 | `PROPOSED` | 最小边界规划 | 二期页面和工程实现 | M7 设计 |

## 4. 最近里程碑

### M33～M35：动态表单

已实现：

- FORM 配置资产发布基础；
- Task 冻结精确 FormVersion；
- 不可变 FormSubmission；
- 固定 required 和基础类型验证；
- Task 完成只接受同 Task、同项目、同冻结版本的有效提交。

未实现：

- 任意函数、计算字段、脚本/决策表（M52～M53 已实现白名单布尔/类型比较条件子集）；
- 复杂 validator；
- 草稿、预填冲突和更正；
- 表单审核闭环。

### M36～M53：Evidence

已实现：

- EVIDENCE 资产发布门禁与固定 EvidenceSlot 解析（M36～M37）；
- EvidenceItem / 不可变 EvidenceRevision 与安全文件 Begin/Finalize（M38）；
- 确定性机器校验与 VALIDATED / VALIDATION_FAILED（M39）；
- 不可变 EvidenceSetSnapshot（TASK_SUBMISSION）（M40）；
- 无 formRef 资料 Task 完成仅接受精确 Snapshot 引用与 digest（M41）；
- 授权 `VALIDATED → INVALIDATED`、槽位投影刷新、历史 Snapshot 不可改写（M42）。
- formRef 且非空 EvidenceSlot Task 以精确 FormSubmission 和 TASK_SUBMISSION Snapshot
  的 `inputVersionRefs` 双引用完成，并同事务持久化（M43）；
- ReviewCase 绑定 TASK_SUBMISSION Snapshot；只追加 APPROVED/REJECTED ReviewDecision（M44）；
- REJECTED 同事务创建 CorrectionCase；补传轮次只追加；RESUBMITTED→CLOSED（M45）；
- Evidence invalidate 同事务联动 StoredFile AVAILABLE→INVALIDATED（M46）；
- CorrectionCase 打开时自动创建 evidence.correction Task 并进入 IN_PROGRESS（M47）；
- OPEN 强制通过 FORCE_APPROVED；APPROVED/FORCE_APPROVED 重开产生新 OPEN 案例（M48）；
- 适配层记录 ExternalReviewReceipt 并追加 EXTERNAL 决定；驳回开客服协调 Task（M49）；
- 整改 Task 自动写入源责任人 CANDIDATE（M50）；
- CorrectionCase 高风险豁免进入 WAIVED 并取消整改 Task（M51）。
- SERVICEOS_EXPR_V1 白名单布尔条件驱动 EvidenceSlot 初次解析；true/false 决策、输入摘要与
  命中解释不可变审计，复杂度和非法配置失败关闭（M52）。
- 同一锁定 FormVersion 的最新 VALIDATED submission 作为权威条件事实；`formValues[...]`
  发布期类型检查与服务端表单条件验证（M53）；
- `form.submitted@v1` 通过 Inbox 触发单 Task 串行、只追加 resolution generation；重复、迟到和
  task.created 乱序不回退事实版本（M53）；
- true→false 保留已提交资料并进入精确代次 REVIEW_REQUIRED；false→true 创建新槽位世代，
  不恢复旧槽位；KEEP/INVALIDATE 处置具备 capability、审计、幂等与 Outbox（M53）；
- Portal 槽位投影、Snapshot 与 CompleteTask 读取同一最新代次，并跨历史代次阻断未决处置（M53）。

未实现：

- OCR / 图像 CV / GPS 权威距离；
- 计算字段、脚本/决策表、草稿冲突与离线表达式合并；
- 多候选人策略评分与自动 claim；
- CLIENT origin ReviewCase 已可在总部审核通过后显式登记（M55）；M57 已将 BYD 厂端回调
  标准化并关联显式订单路由；M58 已在提审明确成功后自动创建 CLIENT Case 和路由；
  remark/Evidence target 自动映射仍未实现。

### M54：车企回执影响对象权威校验

已实现：

- OpenAPI 0.27.0 将 `affectedTargets` 从任意 object 收紧为强类型
  `ExternalReviewAffectedTarget`；
- 该收紧相对 0.26.0 是经项目负责人于 2026-07-15 明确批准的版本化破坏性变更；兼容门禁准确
  报告四个新增必填属性，未加入旧结构兜底、宽松解析或双轨模型；
- 每个目标必须以 slot/item/revision 三元组精确命中 ReviewCase 绑定的不可变
  EvidenceSetSnapshotMember；
- 跨 Snapshot、错配三元组、重复目标、未知类型和超限目标失败关闭；
- 目标校验发生在 ReviewCase 状态迁移前，失败时不产生 ReviewDecision、客服协调 Task、审计或
  Outbox 副作用；
- 合法回执继续保持幂等、不可变和同事务决定/协调 Task/审计/Outbox。

明确未实现：

- 完整 Connector 验签与标准化入站表；
- CLIENT origin ReviewCase 自动创建；
- callbackBatchRef / mappingVersionId 外部权威登记与批次校验；
- 字段、表单、报告等其他 targetType 及自动整改对象映射。

### M55：CLIENT ReviewCase 来源与回执批次门禁

已实现：

- ReviewCase 显式区分 INTERNAL/CLIENT；CLIENT Case 只能从已 APPROVED/FORCE_APPROVED 的 INTERNAL
  Case 派生，并冻结同一 Snapshot 和 contentDigest；
- SERVICE-only 内部命令要求 `evidence.createClientReviewCase`，显式登记 externalSubmissionRef、
  callbackBatchRef、mappingVersionId 和 CLIENT policyVersion；
- CLIENT lineage 由 V054 的 CHECK/FK/唯一索引和不可变 trigger 保护；迁移临时默认值已删除；
- INTERNAL decide/force/reopen 不得裁决 CLIENT Case；外部回执只接受 OPEN CLIENT Case；
- 回执 callbackBatchRef 和 mappingVersionId 必须精确匹配 Case 冻结值，随后继续执行 M54 的
  SnapshotMember 目标校验；
- CLIENT Case、幂等结果、审计和 `evidence.client-review-case-created@v1` Outbox 同事务。

明确未实现：

- 外部审核回调在 M55 时尚未实现，现已由 M57 补齐 BYD 厂端审核回调纵向切片；
- OutboundDelivery、交付成功事件自动创建 CLIENT Case；
- callbackBatchRef / mappingVersionId 对 integration 域登记事实的跨模块权威校验；
- 其他 targetType、自动整改映射、Portal 和二级审批/MFA。

### M56：BYD CPIM InboundEnvelope 与 CanonicalMessage 权威入站事实

已实现：

- 通过 BYD 签名/时间窗校验后，Envelope 与 replay guard 在首个事务登记；无效签名不留入站事实；
- HTTP 原始字节按长度与 SHA-256 写入服务端私有对象存储，普通 API 不暴露对象引用、签名或凭据；
- 映射后的 CanonicalMessage 冻结 connector、消息类型、业务键、规范摘要、mappingVersion 和源 Envelope；
- transport 重放只返回首次结果；新 nonce 的同业务键同摘要复用一个 Canonical/WorkOrder，不同摘要失败关闭；
- Canonical/WorkOrder/Envelope 完成、审计、`integration.canonical-message-processed@v1` Outbox 和响应摘要
  同事务提交；首事务后崩溃可由相同 transport 请求继续 RECEIVED Envelope；
- OpenAPI 0.29.0 提供受 tenant/project scope 与 `integration.readInbound` 保护的 Envelope/Canonical 摘要；
- V055 建立权威表、唯一约束、FK、不可变 trigger 与 replay Envelope 关联。

明确未实现：

- 其他 CPIM messageType（外部审核回调已由 M57 实现）；
- OutboundDelivery、网络 Connector、凭据轮换、自动重试、远端状态查询和人工重放；
- 文件批次/SFTP、Ack/Replay 聚合、原文授权下载；
- 正式对象存储、生产 sandbox/凭据、脱敏真实流量演练与 Portal。

### M57：BYD 厂端审核回调权威入站运行时

已实现：

- 按原始 V7.3.1 协议纠正 `Cur_Time=yyyy-MM-dd` 和
  `AppSecret&Nonce&Cur_Time&Params` 签名原文，未保留 epoch 秒或旧签名兼容分支；
- SERVICE-only 内部命令显式登记 `externalOrderCode -> OPEN CLIENT ReviewCase`，并精确核对
  submission/batch/mapping 冻结事实；
- 一个回调 Envelope 拆分最多 100 个逐订单 CanonicalMessage 和不可变 item result；原文与审核备注
  仅私有留存，普通查询不暴露；
- 已登记订单通过 M49 公共 API 追加 ExternalReviewReceipt/EXTERNAL 决定，驳回继续创建客服协调 Task；
- 路由缺失、Case 冲突和业务摘要冲突失败关闭并幂等创建人工任务，批次返回明确部分成功；
- 同 transport 返回首次批次结果，新 transport 同业务键同摘要复用 Canonical/领域结果；
- 基础设施或授权失败不伪造成功，Envelope 保持 RECEIVED，修复后原请求可恢复；
- Core OpenAPI 0.30.0 的 Canonical `projectId` nullable 和新增回调 messageType 相对 0.29.0 为经项目负责人
  于 2026-07-15 明确批准的版本化破坏性纠正；兼容门禁准确报告 1 个 error、2 个 warning，未加入旧字段、
  默认项目或查询降级；
- V056/V057、Core OpenAPI 0.30.0、BYD OpenAPI 0.2.0、两个事件 Schema、PostgreSQL IT、
  MVC Security、契约/客户端与 Modulith 门禁形成工程证据。

明确未实现：

- BYD 提审 OutboundDelivery 已由 M58 实现；UNKNOWN 人工重发已由 M59 实现；协议未提供的
  远端状态查询不得猜测，人工标记已送达/放弃仍未实现；
- 取消/暂停/恢复等其他 CPIM messageType，文件批次/SFTP 与生产凭据轮换；
- remark 到 EvidenceRevision 的自动 target 映射、完整 OperationalException 聚合和 Portal；
- 正式对象存储、生产 sandbox/凭据和脱敏真实流量演练。

### M58：BYD 提审 OutboundDelivery 可靠运行时

已实现：

- 从已通过 INTERNAL ReviewCase、冻结 Snapshot/Task 和 M56 Canonical lineage 派生不可变提审意图；
- Delivery/Attempt/Acknowledgement 分离持久化，Task 作为唯一重试时钟，网络调用位于数据库事务外；
- 严格按 BYD V7.3.1 2.5 节发送 `operatePerson/orderCode/commitDate`，冻结私有 payload/response 及 digest；
- `errno=0` 后幂等创建 CLIENT ReviewCase 和 M57 回调路由；本地落账重试不二次发 HTTP；
- 明确业务拒绝、发送前最终失败与 UNKNOWN 分开；UNKNOWN 绝不自动重发，并进入
  OperationalException + HUMAN Task；
- V058、Core OpenAPI 0.31.0、BYD OpenAPI 0.3.0、外部 payload 与两个事件 Schema、PostgreSQL IT、
  HTTP/MVC Security、契约/客户端与 Modulith 门禁形成工程证据。

明确未实现：

- UNKNOWN 人工重发已由 M59 实现；人工标记已送达或放弃仍未实现；
- 取消/暂停/恢复等其他 CPIM 消息、文件批次/SFTP 和通用 Connector SDK；
- 生产 Secret Manager/凭据轮换、正式对象存储、真实 sandbox 和脱敏流量演练；
- Evidence target 自动映射、Portal 和完整通知中心。

### M59：UNKNOWN 外部交付人工重发运行时

已实现：

- USER-only 命令要求 HIGH capability、实时 tenant/project scope、原因、审批引用和预期聚合版本；
- 同一 Delivery 与冻结 payload/external key 上登记不可变 ReplayRequest 和新 Task，原 UNKNOWN
  Attempt 永久保留；
- Idempotency、ReplayRequest、Task、Audit 与 Outbox 同事务；网络仍位于 Task 事务外；
- V059 通过单活跃请求唯一索引、生命周期 CHECK 和 trigger 禁止并发双发、授权字段改写及无授权
  UNKNOWN 重开；
- 再次 UNKNOWN 继续人工接管；明确送达后的本地 CLIENT Case/Route 落账重试不再发 HTTP；
- Core OpenAPI 0.32.0、`integration.outbound-delivery-replay-requested@v1`、PostgreSQL IT、MVC
  Security、契约/客户端与 Modulith 门禁形成工程证据。

明确未实现：

- 人工标记已送达、放弃交付和远端状态查询；原 M58 OperationalException/HUMAN Task 在严格 ACK
  恢复事实下的自动闭环已由 M60 实现；
- 批量重放审批、二级审批/MFA、其他 CPIM 消息和通用 Connector；
- 生产 Secret Manager/对象存储/sandbox、Evidence target 自动映射、Portal 和通知中心。

### M60：外部交付恢复异常自动闭环

已实现：

- M59 重发取得严格 ACK 后，同事务追加 `integration.outbound-delivery-recovered@v1`，精确冻结成功
  Task 与同 Delivery 历次执行 Task；
- Operations 通过 Inbox、同源 Task 事务级 advisory lock 和不可变 recovery marker 串行处理失败开单与
  恢复关单；
- 已存在异常转 RESOLVED，未完成 HUMAN Task 经 Task 公共 API 取消；恢复先到时，迟到失败只生成
  RESOLVED 历史且不创建 HUMAN Task；
- marker/异常/Task/Inbox/Outbox 同事务，重复事件幂等、digest 变更失败关闭；
- V060、recovered@v1、operational.exception.resolved@v2、PostgreSQL IT、契约与 Modulith 门禁形成证据。

明确未实现：

- 人工标记已送达、放弃交付以及协议未提供的远端状态查询；
- 其他异常类型自动闭环、完整通知通道、运营中心前端和跨域异常目录；
- 批量重放审批、二级审批/MFA、通用 Connector 和生产基础设施。

### M61：Task 自然时长 SLA 时钟

已实现：

- SLA v1 配置只接受显式 TASK/ELAPSED、开始/停止事件和目标秒数；未知版本、缺失时长或身份错配
  失败关闭；
- Workflow `slaRef` 必须在同一 Bundle 精确命中唯一 SLA 且覆盖节点 taskType；首个和后续 Task 均
  冻结该引用；
- task.created@v1 原子创建 RUNNING instance、RUNNING segment、TARGET_DUE milestone 与
  sla.started@v1；
- task.completed@v1/v2 按业务发生时间形成 MET 或 BREACHED→MET_LATE；breach/met 事件版本严格递增，
  超时历史不被擦除；
- 到期对账以 `FOR UPDATE ... SKIP LOCKED` 锁定实例、里程碑和 Task；Task 已完成但完成事件尚未消费
  时不误报；
- Inbox、SLA 事实和 Outbox 同事务；V061 保护冻结身份、状态迁移、版本、唯一运行 segment 和唯一
  milestone；PostgreSQL IT、事件契约与 Modulith 门禁形成工程证据。

明确未实现：

- BUSINESS 工作日历、节假日和跨日班次；
- 暂停/恢复、免责、重算和取消；
- 预警、升级、通知、OperationalException 联动及收件人解析；
- 其他 subject/start/stop 组合；M61 时尚无 SLA HTTP，已由 M62 补齐只读查询，但 Portal 前端、考核与结算读取仍未实现。

### M62：SLA 授权查询与工作台投影

已实现：

- Core OpenAPI 0.33.0 发布项目 SLA 工作台、工单 SLA 时间线和实例/segment/milestone 详情三个 GET；
- `sla.read` capability 与 RoleGrant 实时 Project Scope 复核；tenant 只来自 JWT，跨 tenant 保持 404；
- 工作台强制显式 projectId；工单查询通过 `workorder::api` 最小 Scope 端口解析 project，不跨模块读表；
- `(deadlineAt, slaInstanceId)` 稳定游标绑定 project/workOrder/status，不能跨筛选条件复用；
- `asOf`、remaining 和 overdue 只按服务端 Clock 计算；已过期但未对账的 RUNNING 不伪造 BREACHED；
- V062 查询索引、PostgreSQL IT、MVC Security、契约兼容/客户端生成与 Modulith 门禁形成工程证据。

明确未实现：

- BUSINESS 日历、暂停/恢复、免责/重算、预警/升级/通知；
- 跨项目/区域/网点的数据范围投影、导出和运营分析；
- Admin/Network/Technician Portal 前端、考核与结算读取；
- 其他 subject/start/stop 组合。

### M63：授权项目集合与跨项目 SLA 队列

已实现：

- authorization 公共端口按实时有效 RoleGrant 解析 TENANT 全项目或多个 PROJECT UUID 集合；
- Core OpenAPI 0.34.0 将 SLA 工作台 projectId 改为可选，省略时一次解析范围并执行单条范围化 SQL；
- REGION/NETWORK 尚无权威项目关系时返回 403 并写拒绝审计，不猜测、不扩大、不静默返回空；
- 游标绑定排序后的授权集合摘要，grant 新增、撤销或过期后旧游标失败关闭；
- V063 tenant 游标索引、Authorization/SLA PostgreSQL IT、MVC Security 与 Modulith 门禁形成工程证据。

明确未实现：

- REGION/NETWORK/组织关系到项目的权威关系投影；
- 授权范围缓存、导出和运营分析；
- BUSINESS 日历、暂停/恢复、免责/重算、预警/升级/通知；
- Portal 前端、考核与结算读取、其他 SLA subject/start/stop 组合。

### M64：项目区域关系与 REGION SLA 队列

已实现：

- Core OpenAPI 0.35.0 为项目创建/响应增加可选 `regionCodes`，省略或空数组明确表示不建立关系；
- Project 聚合校验、排序并在同一事务写入 `prj_project_region`、审计、Outbox 和幂等结果；
- V064 以 tenant/project 复合外键、有效期、唯一约束和范围索引建立 REGION 权威关系；
- authorization 通过公开 `ProjectRegionScopeResolver` 精确解析有效 REGION RoleGrant，不跨模块读表；
- REGION 项目集合复用 M63 单条范围化 SLA 查询，关系变化通过 scope digest 使旧游标失败关闭；
- Project/Authorization/SLA PostgreSQL IT、MVC、契约/客户端和 Modulith 门禁形成工程证据。

明确未实现：

- NETWORK/ServiceNetwork、Organization 到 Project 的权威关系；
- Region 目录、层级后代展开、关系独立修订/终止命令和治理 UI；
- 授权缓存、导出和运营分析；
- BUSINESS 日历、暂停/恢复、免责/重算、预警/升级/通知；
- Portal 前端、考核结算及其他 SLA subject/start/stop 组合。

### M65：项目网点关系与 NETWORK SLA 队列

已实现：

- Core OpenAPI 0.36.0 为项目创建/响应增加可选 `networkIds`，省略或空数组明确表示不建立关系；
- Project 聚合校验、排序并在同一事务写入 `prj_project_network`、审计、Outbox 和幂等结果；
- V065 以 tenant/project 复合外键、有效期、唯一约束和范围索引建立 NETWORK 权威项目关系；
- authorization 经公开 `ProjectNetworkScopeResolver` 精确解析有效 NETWORK RoleGrant；
- NETWORK 项目集合复用单条范围化 SLA 查询，关系变化通过 scope digest 使旧游标失败关闭；
- `project.created@v3`、PostgreSQL IT、MVC、契约/客户端与 Modulith 门禁形成工程证据。

明确未实现：

- ServiceNetwork 目录、生命周期、Coverage/Capability、停派、资质和治理 API；
- Project REGION/NETWORK 关系的独立修订与即时终止已由 M66 实现；计划生效和审批仍未实现；
- Organization/Region 目录、层级后代和组织到项目关系；
- 授权缓存、导出、Network Portal 和运营分析；
- BUSINESS 日历、暂停/恢复、预警/升级/通知以及完整派单策略和初派闭环。

### M66：项目范围关系整组修订

已实现：

- `POST /api/v1/projects/{projectId}:revise-scope-relations` 显式整组替换 REGION/NETWORK 当前关系，
  两个集合和原因均为必填，空集合明确清空，缺字段不表示保持；
- `If-Match`、Project 行锁和条件版本更新阻止并发部分写入；无变化和陈旧版本明确失败；
- 被移除关系结束有效期，新增关系追加，未变关系保留原历史；V066 建立开放关系唯一索引、结束审计约束
  和不可变 `prj_project_scope_revision` 收据；
- 项目版本、关系历史、冻结收据、审计、`project.scope-relations-revised@v1` Outbox 和幂等结果同事务；
- 幂等重放返回首次冻结收据；授权 REGION/NETWORK 映射即时变化，SLA 旧 scope digest 游标失败关闭；
- Core OpenAPI 0.37.0、PostgreSQL 18.4、契约兼容/客户端、ArchitectureTest 与全量 L3 形成工程证据。

明确未实现：

- ServiceNetwork 目录、准入/启用/清退生命周期、Coverage/Capability、合同、停派和资质；
- Organization/Region 目录、层级后代及组织到项目关系；
- 未来生效的计划修订、审批工作流、双人复核和治理 UI；
- 项目 owners、生命周期、服务产品绑定修订；
- 授权缓存/导出、Portal、完整派单、BUSINESS SLA、通知、试算和结算。

### M67：项目授权目录与范围历史查询

已实现：

- `GET /api/v1/projects` 按实时 TENANT/PROJECT/REGION/NETWORK `project.read` RoleGrant 查询授权目录；
- `clientId/status/activeOn` 精确筛选和 `projectCode/projectId` keyset 分页，cursor 绑定范围及筛选摘要；
- 项目详情返回当前核心事实、当前 REGION/NETWORK 关系、聚合 ETag 与服务端 `asOf`；
- 范围修订历史按 `aggregateVersion` 倒序读取 M66 不可变收据，cursor 绑定 projectId；
- 详情与历史先 tenant 隔离后实时鉴权；撤权/越权失败关闭并审计，跨租户资源统一 404；
- V067、Core OpenAPI 0.38.0、PostgreSQL 18、MVC、契约兼容/客户端、ArchitectureTest 与全量 L3
  形成工程证据。

明确未实现：

- Project owners、品牌/服务产品/配置绑定和项目生命周期命令；
- ServiceNetwork/Region/Organization 目录、层级、生命周期与治理 API；
- 计划生效的范围修订、审批/双人复核、导出分析与 Portal 前端；
- 完整派单策略、BUSINESS SLA、通知、试算和结算。

### M68：授权工单目录与详情查询

已实现：

- `GET /api/v1/work-orders` 以实时 `workOrder.read` TENANT/PROJECT/REGION/NETWORK 范围执行单条范围化 SQL；
- client/project/status 精确筛选，receivedAt/id 倒序游标绑定授权范围与筛选摘要；
- 详情先 tenant 隔离再按所属 project 鉴权，越权拒绝审计、跨租户 404；
- 返回生命周期、冻结配置包与区域等现有权威事实，显式排除客户姓名、手机号、地址和 VIN；
- V068、Core OpenAPI 0.39.0、PostgreSQL 18、MVC、契约与 ArchitectureTest 形成工程证据。

明确未实现：客户敏感详情及增强读取审计、阶段/任务/时间线/允许动作、派单与 SLA 风险筛选、Portal。

### M69：授权工单执行工作区投影

已实现：

- `GET /api/v1/work-orders/{id}/stages` 返回冻结 Workflow 与 sequenceNo 正序 Stage 当前事实；
- `GET /api/v1/work-orders/{id}/tasks` 以 createdAt/taskId 正序稳定分页返回最小 Task 执行摘要；
- 两条查询复用 M68 工单 tenant 隔离和 `workOrder.read` 项目鉴权，各模块只读取自己的表；
- 尚未异步初始化时显式返回 null/空集合，不伪造 Workflow 成功；
- 响应排除 payload、resultRef、inputVersionRefs、错误正文和客户 PII；
- V069、Core OpenAPI 0.40.0、PostgreSQL 18、MVC、契约与 ArchitectureTest 形成工程证据。

M69 当时未实现的 Task 独立队列/详情、允许动作与自动 Task Attempt 历史已分别由 M70～M72 补齐。
完整工单时间线、Workflow Node/HUMAN 命令历史、客户敏感信息和 Portal 仍未实现。

### M70：授权任务队列与详情

已实现：

- `GET /api/v1/tasks` 以实时 `task.read` TENANT/PROJECT/REGION/NETWORK 范围查询独立任务队列；
- 支持 project、taskKind、status 与 `assignee=me` 精确筛选，游标绑定授权范围和全部筛选条件；
- `assignee=me` 只匹配当前主体已有的 ACTIVE CANDIDATE/RESPONSIBLE 事实，不猜测默认负责人；
- `GET /api/v1/tasks/{taskId}` 先 tenant 隔离再按 project 或 tenant capability 鉴权，返回冻结配置、流程、表单、结果引用、输入版本和责任事实；
- 列表不暴露 payload/result/input/error 正文，详情也不暴露 payload 与错误正文；
- V070、Core OpenAPI 0.41.0、PostgreSQL 18、MVC、契约与 ArchitectureTest 形成工程证据。

M70 当时未实现的动态允许动作与自动 Task Attempt 历史已分别由 M71～M72 补齐。Workflow Node/HUMAN
命令历史、SLA 聚合、客户 PII、Portal 仍未实现；任何默认候选人推断仍被禁止。

### M71：Task 服务端允许动作投影

已实现：

- `GET /api/v1/tasks/{taskId}/allowed-actions` 先复用 M70 tenant 隔离与实时 `task.read` 鉴权；
- 只投影已实施的 `task.claim/start/complete/release`，并使用与写命令相同的 capability 请求；
- READY 候选人、CLAIMED 当前责任人、RUNNING 当前责任人与流程节点事实决定动作，ACTIVE execution
  guard 使全部人工命令动作消失；
- RoleGrant 撤销、Task 版本、责任和 guard 均实时读取，不信任 JWT capability 或客户端状态；
- 响应返回 `resourceVersion`、稳定 action descriptor、输入 schema 引用、obligations 与服务端 `asOf`；
- Core OpenAPI 0.42.0、PostgreSQL 18、MVC、契约、客户端生成和 ArchitectureTest 形成工程证据；
  无数据库结构变化，Flyway 保持 V070 / 72。

明确未实现：block/resolve-block、retry、cancel、manual-complete、完成条件预演、Workflow Node、
跨工单/跨域完整命令历史、SLA 聚合和 Portal；自动 Task Attempt 历史与工单内核心 Task 生命周期
分别由 M72～M73 补齐。allowed-actions 不是授权凭证，
写命令仍失败关闭。

### M72：Task 执行 Attempt 历史查询

已实现：

- `GET /api/v1/tasks/{taskId}/execution-attempts` 每页先复用 M70 tenant 隔离与实时 `task.read` 鉴权；
- 自动 Task 已有 Attempt 按 `attemptNo DESC` 稳定分页，cursor 绑定 taskId 且不能跨 Task 复用；
- HUMAN Task 明确返回带当前资源版本的空页，不伪造人工命令历史；
- 返回 Attempt 标识、结果码、安全错误码、结果引用、重试与起止时间，不暴露 workerId、payload、
  错误正文、私有响应或凭据；
- RoleGrant 撤销后下一页立即 403 并记录拒绝审计，跨 tenant 保持 404；
- Core OpenAPI 0.43.0、PostgreSQL 18、MVC、契约、客户端生成和 ArchitectureTest 形成证据；复用 V008
  的 `(task_id, attempt_no DESC)` 索引，无数据库结构变化，Flyway 保持 V070/72。

M72 当时未实现的工单内核心 Task 生命周期时间线已由 M73 补齐。Workflow Node 历史、跨工单/跨域
完整时间线、Attempt retry/manual-complete 写命令、错误正文授权读取、SLA 聚合和 Portal 仍未实现。

### M73：工单核心执行时间线投影

已实现：

- 按 ARCH-19 建立独立 `readmodel` Modulith 模块，不回写 WorkOrder/Workflow/Task 领域事实；
- Inbox 可靠消费 WorkOrder、Workflow、Stage、Task 已发布核心生命周期事件，重复幂等、digest 变化失败关闭；
- Task 简化事件仅通过 `task::api` 最小上下文解析 workOrder/project，不跨模块读取 `tsk_*`；
- tenant、aggregate/resource、Project、发生时间任一错配时投影与 Inbox 整体回滚；
- V071 建立 `rdm_work_order_timeline_entry` 和业务时间稳定分页索引，投影可清空重建且无跨模块 FK；
- `GET /api/v1/work-orders/{id}/timeline` 每页复用 M68 实时 `workOrder.read` 鉴权，撤权 403 + 拒绝审计，
  跨 tenant 404，cursor 绑定工单；
- 响应保留 occurred/received 双时间、资源/主体/correlation 和模板版本，不保存或返回 payload、PII、
  resultRef、错误正文、签名或凭据；无 Broker checkpoint 时显式返回 `freshnessStatus=UNKNOWN`；
- Core OpenAPI 0.44.0、PostgreSQL 18、Inbox、MVC、契约、客户端生成与 ArchitectureTest 形成证据。

明确未实现：Appointment、Visit、Evidence/Review、Delivery、SLA、OperationalException、试算/结算
事件合并，correlation 展开、敏感字段二次授权、重建作业、Broker checkpoint、搜索、导出和 Portal。

### M74：工单现场履约时间线事件合并

已实现：

- 同一 `readmodel` Inbox 消费者合并已发布 ContactAttempt / Appointment / Visit 事件；
- 载荷已含 workOrderId/projectId，不新增跨模块表读取；身份错配失败关闭；
- 不投影 contactedPartyRef、noShowPartyRef、GPS、evidenceRefs、note 或自由文本；
- V072 expand category 约束；Core OpenAPI 0.45.0 扩展 timeline 枚举；
- 查询/授权/分页/UNKNOWN freshness 与 M73 一致；PostgreSQL、Inbox、MVC、契约与 ArchitectureTest 证据。

明确未实现：Evidence/Review、Delivery、SLA、OperationalException、试算/结算事件合并，
correlation 展开、重建作业、Broker checkpoint、搜索、导出和 Portal。

### M75：工单 SLA 时间线事件合并

已实现：

- 同一 readmodel Inbox 合并 `sla.started` / `sla.breached` / `sla.met`；
- started 直接使用载荷 workOrderId；breached/met 经 TaskTimelineContextQuery 解析；
- 非工单 Task SLA 明确忽略；不投影 digest、elapsedSeconds 或自由文本；
- V073 expand `SLA` category；Core OpenAPI 0.46.0 扩展 x-extensible-enum；
- 查询授权/分页/UNKNOWN freshness 不变。

明确未实现：Evidence/Review、Delivery、OperationalException、试算/结算合并，BUSINESS 日历、
暂停/预警/升级事件，checkpoint、重建作业、搜索、导出和 Portal。

### M76：工单资料审核时间线事件合并

已实现：

- 同一 readmodel Inbox 合并 form.submitted、set-snapshotted、review/correction 生命周期事件；
- 全部经 TaskTimelineContextQuery 解析工单；projectId 错配失败关闭；
- 不投影 reason 正文、reasonCodes、digest 或 note；
- V074 expand FORM/EVIDENCE/REVIEW/CORRECTION；Core OpenAPI 0.47.0。

明确未实现：revision 技术校验噪声、slots 解析、external receipt、Delivery、异常、试算/结算、
checkpoint/重建、Portal。

### M77：工单外发交付与异常闭环时间线事件合并

已实现：

- `integration.outbound-delivery-created` 直接按 sourceWorkOrderId 投影；
- `operational.exception.resolved@v2` 经 TaskTimelineContextQuery；
- V075 expand DELIVERY/EXCEPTION；Core OpenAPI 0.48.0。

明确未实现：delivery acknowledged/recovered/replay、exception acknowledged、试算/结算、
checkpoint/重建、Portal。

### M78：工单外发交付确认与恢复时间线事件合并

已实现：

- `integration::api` `DeliveryTimelineContextQuery` 最小身份端口；
- 投影 acknowledged / recovered / replay-requested；不保存 orderCode/digest/reason；
- Core OpenAPI 0.49.0；无新 Flyway（沿用 V075 DELIVERY category）。

明确未实现：试算/结算、checkpoint/重建、Portal。

### M79：工单运营异常确认时间线事件合并

已实现：

- `operations::api` `ExceptionTimelineContextQuery` 最小身份端口；
- 投影 `operational.exception.acknowledged@v1`；无 Task 链接则忽略投影；
- Core OpenAPI 0.50.0；无新 Flyway（沿用 V075 EXCEPTION category）。

明确未实现：试算/结算、checkpoint/重建、Portal。

### M80：工单服务分配生命周期时间线事件合并

已实现：

- 同一 readmodel Inbox 合并 ServiceAssignment 激活握手/完成/超时事件；
- category `ASSIGNMENT`，不投影 assignee/capacity/guard；
- V076 + Core OpenAPI 0.51.0。

明确未实现：ServiceNetwork 生命周期、试算/结算、checkpoint/重建、Portal。

### M81：工单 Task 指派与执行保护时间线事件合并

已实现：

- 同一 readmodel Inbox 合并 task.assigned / assignment-* / execution-guard.* /
  manual-intervention-required；经 TaskTimelineContextQuery；
- category 沿用 `TASK`；Core OpenAPI 0.52.0；无新 Flyway。

明确未实现：evidence revision 噪声、checkpoint/重建、ServiceNetwork、试算/结算、Portal。

### M82：工单外部审核回执时间线事件合并

已实现：

- `evidence::api` `ReviewTimelineContextQuery` 最小身份端口；
- 投影 `evidence.external-review-receipt-recorded@v1`；
- Core OpenAPI 0.53.0；无新 Flyway。

明确未实现：revision/slots 噪声、checkpoint/重建、试算/结算、Portal。

### M83：工单条件处置时间线事件合并

已实现：

- 同一 readmodel Inbox 合并 `evidence.condition-disposition-recorded@v1`；
- outcome 取 KEEP/INVALIDATE；不投影 reviewRef/reasonCode/slot；
- Core OpenAPI 0.54.0；无新 Flyway。

明确未实现：revision/slots 技术噪声、试算/结算、Portal。

### M84：工单时间线投影 Checkpoint 与重建

已实现：

- DATA-06 §1/§2/§13 窄切片 Accepted；`work-order-core-timeline.v1` checkpoint、dead letter、rebuild generation；
- 查询 `freshnessStatus` 支持 FRESH/LAGGING/UNKNOWN/REBUILDING；
- Core OpenAPI 0.55.0；Flyway V077 / 79 migrations；
- 经 `reliability::spi` `PublishedOutboxEventReader` 扫描 PUBLISHED 事件重建，禁止跨模块读 `rel_*`。

明确未实现：工作区/队列/SavedView/搜索、多投影平台、Broker offset、试算/结算、Portal、Admin 重建 HTTP API。

### M85：工单工作区只读组合快照

已实现：

- API-06 §2 查询元数据与 §5 顶层 `GET /work-orders/{id}/workspace` 窄切片 Accepted；
- `readmodel` 实时组合 WorkOrderView、当前 Task、时间线 freshness、可选 SLA/异常摘要；
- 缺 `sla.read` / `operations.exception.read` 时对应区块 UNAVAILABLE，工作区仍 200；
- 不含客户 PII；Core OpenAPI 0.56.0；无新 Flyway。

明确未实现：sections 按需加载、队列/SavedView/搜索、Portal、工作区持久化投影、试算/结算。

### M86：工单时间线投影运行时硬化

已实现：

- DATA-06 §2/§13 窄切片补齐：`rdm_projection_definition` 种子；
- dead letter 按 eventId 幂等 REPLAYED / 源缺失 DISCARDED；
- FAILED 无开放 DL 时恢复 RUNNING，并清理孤儿 generation；
- 重建切换成功后清理旧 generation 条目与 checkpoint；
- Flyway V078 / 80；OpenAPI 保持 0.56.0（无新公开 HTTP）。

明确未实现：Admin 重建/重放 HTTP、多投影平台、Broker offset、长观察窗、队列/SavedView/Portal。

### M87：工单工作区按需区块加载

已实现：

- API-06 §5 `workspace/sections/{section}` 窄切片 Accepted（仅 TASKS / TIMELINE_AUDIT）；
- 实时组合 Task 摘要分页与时间线分页；未接受 section → 400；
- Core OpenAPI 0.57.0；无新 Flyway。

明确未实现：其余 section、activity-summary、队列/SavedView、Portal、区块持久化投影。

### M88：工单工作区预约与到访区块

已实现：

- API-06 §5 `APPOINTMENTS_VISITS` 窄切片 Accepted；
- 实时组合 Visit（按工单）与 Appointment（按 Task 扇出）；缺 `visit.read`/`appointment.read`
  时该半边降级为 null，顶层 sectionAvailability 标记 UNAVAILABLE/EMPTY/AVAILABLE；
- 载荷不含 GPS、地址引用、device、note；本切片不接受 cursor 深分页；
- Core OpenAPI 0.58.0；无新 Flyway；readmodel 增加 `fieldwork::api` / `appointment::api` 依赖。

明确未实现：其余 section、contact-attempts、工单级 Appointment 列表端口、队列/SavedView、Portal、
区块持久化投影。

### M89：工单工作区表单与资料区块

已实现：

- API-06 §5 `FORMS_EVIDENCE` 窄切片 Accepted；
- 实时按 Task 扇出 Form 绑定与 EvidenceSlot；缺 `form.read`/`evidence.read` 时该半边降级为 null；
- 载荷不含 definitionJson / requirementDefinition / resolutionExplanation / 提交 values；
- 未完成槽位解析（TASK_STATE_CONFLICT）时该 Task 资料半边按空处理，避免顶层工作区失败；
- Core OpenAPI 0.59.0；无新 Flyway；readmodel 增加 `forms::api` 依赖。

明确未实现：其余 section、FormSubmission 列表、EvidenceItem/Revision 明细、队列/SavedView、Portal、
区块持久化投影。

### M90：工单工作区审核与整改区块

已实现：

- API-06 §5 `REVIEWS_CORRECTIONS` 窄切片 Accepted；
- 实时按 Task 扇出 ReviewCase 与 CorrectionCase；统一经 `evidence.read` 与 Project Scope 鉴权；
- 载荷不含审核 note / approvalRef / decidedBy 与整改 waiveNote 等自由文本或操作者信息；
- 缺读权时子集降级为 null；顶层 availability 保持 UNAVAILABLE/EMPTY/AVAILABLE；
- Core OpenAPI 0.60.0；无新 Flyway。

明确未实现：其余 section、审核整改命令聚合、队列/SavedView、Portal、区块持久化投影。

### M91：工单工作区集成区块

已实现：

- API-06 §5 `INTEGRATION` 窄切片 Accepted；
- 入站只列出明确映射到 WorkOrder 的 Envelope；外发按 source_work_order_id 列出 Delivery；
- `integration.readInbound` / `integration.readOutbound` 与 Project Scope 分别鉴权和降级；
- 不扩散对象引用、payload/signature、operator、幂等键与重放 reason/approvalRef/requestedBy；
- Core OpenAPI 0.61.0；Flyway V079 / 81 migrations。

明确未实现：FACTS_CALCULATIONS、审核回调批次额外归属、专项队列/SavedView、Portal、区块持久化投影。

### M92：工单工作区服务责任摘要

已实现：

- API-06 §5 顶层 `serviceAssignmentSummary` 窄切片 Accepted；
- 当前 Task ACTIVE 网点/师傅责任实时组合，分别保留生效时间与稳定改派原因码；
- `dispatch.read` + Project Scope 授权；缺权 UNAVAILABLE、有权无事实 EMPTY；
- 不扩散 assignment/saga/guard/decision/authority 内部 ID、容量、评分或操作者；
- Core OpenAPI 0.62.0；Flyway V080 / 82 migrations。

明确未实现：FACTS_CALCULATIONS、历史责任、PENDING_ACTIVATION/saga/容量详情、ServiceNetwork 名称、
队列/SavedView、Portal。

### M93：工单最近活动摘要

已实现：

- API-06 §5 `GET /work-orders/{id}/activity-summary` 窄切片 Accepted；
- 复用时间线最近 N 条与 `workOrder.read` 实时 Project Scope，不创建第二份投影；
- 默认 5、最大 20，不接受 cursor，不猜测尚未定义的“关键事件”分类；
- 响应复用最小化 WorkOrderTimelineItem 与 projection freshness/meta；
- Core OpenAPI 0.63.0；无新 Flyway，保持 V080 / 82 migrations。

明确未实现：关键事件 taxonomy/过滤、correlation 展开、FACTS_CALCULATIONS、
customer/location 敏感区块、队列/SavedView、Portal。

### M94：工单工作区联系尝试摘要

已实现：

- M88 `APPOINTMENTS_VISITS` 增加 `contactAttempts` 安全摘要；
- 按 Task 扇出 Appointment 联系事实，复用 `appointment.read` 与实时 Scope；
- startedAt 倒序稳定排序并独立应用 limit；缺权 null、有权无事实空列表；
- 不扩散 contactedPartyRef、note、recordingRef、actorId；
- Core OpenAPI 0.64.0；无新 Flyway，保持 V080 / 82 migrations。

明确未实现：工单级 ContactAttempt 列表端口、敏感联系详情、FACTS_CALCULATIONS、
customer/location、队列/SavedView、Portal。

### M95：工单工作区表单提交与资料项安全元数据

已实现：

- M89 `FORMS_EVIDENCE` 增加 `formSubmissions` / `evidenceItems`；
- Forms/Evidence 独立 summary-only 端口与 SQL，不加载业务值或完整 Revision 图；
- 复用 form.read/evidence.read 与 Project Scope，未解析资料 Task 继续失败关闭并跳过；
- 仅返回校验计数、Revision 数量与最新状态，不扩散敏感正文、文件与操作者；
- Core OpenAPI 0.65.0；无新 Flyway，保持 V080 / 82 migrations。

明确未实现：表单值与资料版本详情、跨 Task cursor、FACTS_CALCULATIONS、
customer/location、队列/SavedView、Portal。

### M96：工单工作区审核血缘元数据

已实现：

- REVIEWS_CORRECTIONS ReviewCase 摘要增加 CLIENT source/submission/batch/mapping 血缘；
- 增加 reopenedFromReviewCaseId/reopenTriggerRef 重开链；
- 复用既有 evidence.read、Project Scope 与 listForTask，不改变状态机/事务/数据库；
- 继续排除 snapshot digest、createdBy、决定/豁免文本和操作者；
- Core OpenAPI 0.66.0；无新 Flyway，保持 V080 / 82 migrations。

明确未实现：回调批次多工单归属、审核队列/命令聚合、FACTS_CALCULATIONS、
customer/location、SavedView、Portal。

### M97：授权审核案例队列

已实现：

- API-06 §6 `GET /review-cases` 窄切片 Accepted；
- evidence.review 实时 TENANT/PROJECT/REGION/NETWORK 项目范围与单条 SQL；
- OPEN 默认，project/status/origin/task 受控筛选，FIFO 游标绑定 scopeDigest 与全部条件；
- 队列仅返回安全 Case/血缘/最新决定代码，不含 snapshot digest、正文、审批或操作者；
- Core OpenAPI 0.67.0；Flyway V081 / 83 migrations。

明确未实现：通用 work-queues/SavedView、SLA/assignee/target enrich、Correction/Outbound 队列、
FACTS_CALCULATIONS、customer/location、Portal。

### M98：授权整改案例队列

已实现：

- API-06 §6 `GET /correction-cases` 窄切片 Accepted；
- evidence.read 实时 TENANT/PROJECT/REGION/NETWORK 项目范围与单条 SQL；
- OPEN 默认，project/status/task/sourceReviewCase 受控筛选，FIFO 游标绑定 scopeDigest 与全部条件；
- 队列仅返回安全 Case/来源审核引用/原因码/补传次数，不含 digest、操作者或豁免/关闭正文；
- Core OpenAPI 0.68.0；Flyway V082 / 84 migrations。

明确未实现：通用 work-queues/SavedView、SLA/assignee enrich、异常队列 Project Scope 硬化、
FACTS_CALCULATIONS、customer/location、Portal。

### M99：授权外发交付队列

已实现：

- API-06 §6 `GET /outbound-deliveries` 窄切片 Accepted；
- integration.readOutbound 实时 TENANT/PROJECT/REGION/NETWORK 项目范围与单条 SQL；
- UNKNOWN 默认，project/status/messageType/workOrder/review 受控筛选，FIFO 游标绑定 scopeDigest 与全部条件；
- 队列仅返回安全 Delivery 身份与 attemptCount，不含 digest、对象引用、操作者或重放审批正文；
- Core OpenAPI 0.69.0；Flyway V083 / 85 migrations。

明确未实现：通用 work-queues/SavedView、异常队列 Project Scope 硬化、人工标记已送达/放弃、
FACTS_CALCULATIONS、customer/location、Portal。

### M100：运营异常项目范围硬化

已实现：

- API-06 §6 `GET /operational-exceptions` 项目范围硬化 Accepted；
- operations.exception.read 实时 TENANT/PROJECT/REGION/NETWORK 范围与 scopeDigest 游标；
- 新增 projectId 筛选与响应字段；无 project 孤儿仅 TENANT 范围可见；
- Core OpenAPI 0.70.0；Flyway V084 / 86 migrations。

明确未实现：通用 work-queues/SavedView、人工标记已送达/放弃、通用 RESOLVED UI、Portal。

### M101：Admin Portal 队列外壳

已实现：

- `serviceos-admin-web`（Vue + TypeScript + Vite）；
- 审核/整改/外发/异常只读队列页与本地 JWT 携带；
- `npm run build` 通过。

明确未实现：设计系统、SavedView、工作区全页、命令 UI、Network/Technician、OIDC SDK、E2E。

### M102：Admin 工单工作区只读外壳

已实现：

- `/work-orders` 查找与 `/work-orders/:id` 工作区只读页；
- 消费 workspace / activity-summary / sections；
- 外发队列深链；`npm run build` 通过。

明确未实现：命令 UI、SavedView、OIDC SDK、设计系统、E2E、Network/Technician。

### M103：Admin 工作区 allowed-actions 只读投影

已实现：

- 工作区展示当前任务服务端 allowed-actions；
- 不本地推导、不执行命令；`npm run build` 通过。

明确未实现：命令执行 UI、OIDC SDK、SavedView、设计系统、E2E。

### M104：Admin 授权工单目录

已实现：

- Admin `/work-orders` 授权目录与筛选；
- 深链到工作区；`npm run build` 通过。

明确未实现：SavedView、命令执行 UI、OIDC SDK、设计系统、E2E。

### M105：Admin 人工任务命令面板

已实现：

- 工作区按 allowed-actions 执行 claim/start/complete/release；
- Idempotency-Key + If-Match；成功后刷新；`npm run build` 通过。

明确未实现：表单/资料提交流程编排、OIDC SDK、SavedView、设计系统、E2E。

### M106：Admin 授权任务目录

已实现：`GET /tasks` 目录页、status/taskKind/assignee=me 筛选、工作区深链；`npm run build`。

明确未实现：任务详情独立页、SavedView、OIDC SDK、E2E。

### M107：Admin SLA 工作台

已实现：`GET /sla-instances` 工作台、status 筛选、工作区深链；`npm run build`。

明确未实现：SLA 详情操作 UI、BUSINESS 日历、预警通知、E2E。

### M108：Admin 授权项目目录

已实现：`GET /projects` 目录页、status/clientId 筛选；`npm run build`。

明确未实现：项目创建/范围修订 UI、配置治理、OIDC SDK、E2E。

### M109：Admin 任务详情

已实现：任务详情、Attempt 历史、命令面板复用、目录深链；`npm run build`。

### M110：Admin 项目详情与范围历史

已实现：项目详情与 scope-revisions 分页、目录深链；`npm run build`。

### M111：Admin 运营异常确认命令

已实现：按 allowedActions 执行 ACKNOWLEDGE（Idempotency-Key/If-Match）；`npm run build`。

### M112：Admin 审核案例详情与裁决

已实现：详情 + decide/force-approve/reopen；队列深链。

### M113：Admin 整改案例详情与命令

已实现：详情 + resubmit/close/waive；队列深链。

### M114：Admin 外发交付详情与人工重发

已实现：详情 + UNKNOWN retry。

### M115：Admin SLA 实例详情

已实现：getSlaInstance 与 segments/milestones 展示。

### M116：Admin 任务表单提交切片

已实现：任务详情表单列表与 JSON 提交，VALIDATED 回填 complete 引用。

### M117：Admin 任务资料快照切片

已实现：槽位/资料项列表与 createEvidenceSetSnapshot，回填 complete 引用。

### M118：Admin BYD 提审外发创建

已实现：审核详情对通过案例创建 BYD review submission OutboundDelivery。

### M119：Admin 资料 Begin/PUT/Finalize 上传

已实现：任务详情资料上传 begin→PUT→finalize，SHA-256 校验。

### M120：Admin 资料条件变化处置

已实现：REVIEW_REQUIRED 槽位 KEEP/INVALIDATE。

### M121：Admin 从资料快照创建审核案例

已实现：快照后 createReviewCase 并深链审核详情。

### M122：Admin 任务联系历史与记录

已实现：任务详情 contact-attempts 列表与追加。

### M123：Admin 任务预约提议确认取消

已实现：propose/confirm/cancel Appointment。

### M124：Admin 上门签到签退

已实现：预约 check-in 与 Visit check-out。

### M125：Admin 预约改约与爽约

已实现：reschedule / mark-no-show。

### M126：Admin 上门中断

已实现：Visit interrupt。

### M127：Admin 资料下载授权与 Revision 作废

已实现：authorizeFileDownload 与 invalidateEvidenceRevision。

### M128：Admin 创建项目

已实现：项目目录 createProject。

### M129：Admin 修订项目范围关系

已实现：revise-scope-relations。

### M130：Admin 分配任务候选

已实现：assign-candidates MANUAL。

### M131：Admin 运营异常详情

已实现：异常详情页与确认命令、队列深链。

### M132：Admin 工单 SLA 实例列表

已实现：工作区 listWorkOrderSlaInstances。

### M133：Admin 表单资料详情与文件作废

已实现：按 ID 读取提交/资料/快照，以及 invalidateStoredFile。

### M134：Admin 工单权威投影与核心时间线

已实现：工单详情、Stage、Task 摘要与核心时间线；Admin 试点局部读写基线（含 APPROVED 完结、
WAIVE、FORCE_APPROVED/reopen）的 PR 阻断 E2E。详见 `docs/admin-pilot-readiness-baseline.md`。

### M135：Admin 正常整改补传 / 关闭 / 复审写链路 E2E

已实现：

- Admin 资料上传在槽位已有 Item 时传入 `evidenceItemId`，于同一 Item 追加补传 Revision，
  避免 `maxCount=1` 阻断正常补传；补传 Snapshot 剔除同 Item 旧成员；
- 第四套每轮新建动态夹具 + Playwright 证明：REJECTED → 源 Task 新 Snapshot → resubmit →
  close → 新 INTERNAL ReviewCase APPROVED → 双引用 complete → WorkOrder FULFILLED；
- `verify-admin-smoke.sh` SQL 断言补传轮次、CLOSED、复审 APPROVED、完结与审计/Inbox；
- 无 uuidgen 环境使用 python3 生成夹具 UUID。

明确未实现：外部提审与回执同一浏览器链、ServiceAssignment Admin 写表面、
从入站接单开始的完整 `ADMIN-PILOT-09`、正式企业 IdP/BFF、生产对象存储/专业扫描；详见
`docs/admin-pilot-readiness-baseline.md` 与
`architecture/148-m135-admin-correction-resubmit-rereview-e2e.md`。

### M136：Admin 预约 / 上门写链路 E2E

已实现：

- 本地 RoleGrant 追加预约/上门写能力；
- 第五套动态夹具注入 ACTIVE NETWORK/TECHNICIAN ServiceAssignment，对齐 Visit 责任；
- Playwright 证明 propose→confirm→check-in→check-out；
- SQL 断言 Appointment/Visit COMPLETED、四类审计与四条事件 Inbox。

明确未实现：用户确认渠道、GPS BLOCK 策略演练、派单 Admin HTTP、外部提审 stub/回调、完整
`ADMIN-PILOT-09`；详见 `architecture/149-m136-admin-appointment-visit-e2e.md`。

### M137：Admin BYD 提审外发 ACK E2E

已实现：

- 获权 USER/SERVICE 可创建 BYD 提审交付；OpenAPI 0.71.0；
- 第六套夹具登记 CREATE_WORK_ORDER Canonical 系谱；本地 stub 严格 errno=0；
- Admin 审核页创建外发后详情可见 ACKNOWLEDGED，并自动创建 CLIENT ReviewCase；
- 本地 BYD adapter SERVICE 主体获 CLIENT Case/Route 能力。

明确未实现：真实 sandbox、其他 CPIM、入站接单完整链。

### M138：Admin BYD 厂端审核回调联调 E2E

已实现：

- 冒烟 Backend 使用 `tenant-local` 作为 BYD CPIM 租户，与交付同租户；
- Admin 外发详情展示 CLIENT 案例链接；
- CPIM 签名厂端回调（result=1）→ EXTERNAL Receipt/Decision → CLIENT APPROVED；
- Playwright + SQL 断言闭环。

明确未实现：真实 sandbox、REJECTED 回调浏览器路径、入站 CREATE_WORK_ORDER 接单链。

### M139：Admin BYD 入站 CREATE_WORK_ORDER 接单 E2E

已实现：

- 冒烟 Backend 绑定 `tenant-local` + `ADMIN-PILOT`，使入站配置解析命中试点 Bundle；
- Playwright 以 CPIM 签名 POST `/install-orders` 创建唯一 orderCode 的 RECEIVED 工单；
- Admin 目录按 RECEIVED 过滤打开工作区，INTEGRATION 区块可见 CREATE_WORK_ORDER COMPLETED；
- SQL 断言 Envelope/Canonical/审计/Outbox 闭环。

明确未实现：入站工单激活与派单、同单完整履约贯通、专用入站队列页、真实 sandbox、完整
`ADMIN-PILOT-09`。

### M140：Admin 入站激活与同单预约上门 E2E

已实现：

- ADMIN-PILOT WORKFLOW 改为可解析线性 USER_TASK，入站后 Outbox 自动 ACTIVE + HUMAN Task；
- 冒烟脚本真实 CPIM 入站并注入 Visit 所需 SA 夹具（非 Admin 派单 HTTP）；
- Playwright 在同一入站工单上 assign-candidates/claim/start 与 propose→confirm→check-in→check-out；
- SQL 断言 Envelope/Canonical/Workflow/Appointment/Visit/审计。

明确未实现：Admin 派单 HTTP、同单表单/资料/审核/外发贯通、专用入站队列页、真实 sandbox、完整
`ADMIN-PILOT-09`。

### M141：Admin 入站同单表单/资料/审核/外发 E2E

已实现：

- 入站 Canonical `business_key` 对齐 `BYD:INSTALL:{orderCode}`，HTTP 响应仍返回裸 orderCode；
- ADMIN-PILOT Bundle 增加 formRef + FORM/EVIDENCE（stage=PILOT_SURVEY）；
- 同一入站工单 Playwright：预约上门 → 表单/资料 → INTERNAL APPROVED → BYD ACK → 厂端回调 →
  双输入 complete → FULFILLED；
- SQL 断言系谱前缀、formRef、Submission/Snapshot、INTERNAL/Outbound/CLIENT 与完结状态。

明确未实现：Admin 派单 HTTP、同单整改分支、专用入站队列页、真实 sandbox、完整 `ADMIN-PILOT-09`。

### M142：Admin 入站同单整改补传复审外发 E2E

已实现：

- 同一入站工单 Playwright：预约上门 → 表单/首轮资料 → INTERNAL REJECTED → CorrectionCase
  → 同 Item 补传 → resubmit/close → 复审 APPROVED → BYD ACK → 厂端回调 → dual complete → FULFILLED；
- SQL 断言双 Snapshot、REJECTED/CLOSED/APPROVED、Outbound/CLIENT 与完结状态；
- ServiceAssignment 仍为本地夹具（M143 起改为 SPI 种子）。

明确未实现：Admin 派单 HTTP、专用入站队列页、真实 sandbox、完整 `ADMIN-PILOT-09`。

### M143：Admin 试点 SPI ServiceAssignment 种子

已实现：

- field-ops 与入站工单经 `CapacityAuthorityService` + `ServiceAssignmentService` SPI 注入
  ACTIVE NETWORK/TECHNICIAN、CONFIRMED reservation 与 COMPLETED saga；
- 冒烟脚本删除 SQL 直插；SQL 断言 `1:1:2:2`；默认 CI 不执行种子入口；
- 种子主体能力与本地项目管理员隔离。

明确未实现（当时）：Admin 派单 HTTP、专用入站队列页、真实 sandbox、完整 `ADMIN-PILOT-09`。
M144 起 SPI 种子入口已删除。

### M144：Admin 人工初派 ServiceAssignment HTTP

已实现：

- `POST /tasks/{taskId}/service-assignments:manual-assign`（Core OpenAPI 0.72.0）；
- 编排门面同事务激活 NETWORK+TECHNICIAN；MVC 安全测试 + PostgreSQL IT；
- Admin 工作区人工初派控件；field-ops/入站 Playwright；删除 M143 SPI 种子；
- 入站路径证明 `ADMIN-PILOT-09`（派单为窄化 Manual Assign）。

明确未实现：评分/硬过滤/DispatchDecision、ServiceNetwork 生命周期、专用入站队列页、真实 sandbox。

### M167：Admin Task 面板资源详情深链

已实现：

- TaskFieldOpsPanel → ContactAttempt / Appointment / Visit；
- TaskFormsEvidencePanel → FormSubmission / EvidenceItem；
- Playwright `ADMIN-PILOT-08TP`。

明确未实现：FieldOperation 详情、SavedView、企业 OIDC/BFF、真实 sandbox。

### M166：Admin 工作区审核/整改关联资源深链

已实现：

- 工作区 `REVIEWS_CORRECTIONS` → Snapshot / 源审核 / 整改源审核 / 最近补传快照；
- 仅使用 Accepted 投影字段；前缀 `rc /` 避免严格模式冲突；
- Playwright `ADMIN-PILOT-08RW`。

明确未实现：FieldOperation 详情、SavedView、企业 OIDC/BFF、真实 sandbox。

### M165：Admin 工作区异常摘要 → 异常队列 query 水合

已实现：

- 工作区 `exceptionSummary` → `/exceptions?workOrderId&status=OPEN`；
- `ExceptionQueuePage` 从 `route.query` 水合 Accepted OpenAPI 筛选；
- Playwright `ADMIN-PILOT-08EH`。

明确未实现：FieldOperation 详情、SavedView、多 status OR、企业 OIDC/BFF、真实 sandbox。

### M164：Admin 审核/整改详情交叉深链

已实现：

- 审核详情 → 资料快照 / 源审核案例（重开后继）；
- 整改详情 → 源资料快照 / 最近补传快照；
- Playwright `ADMIN-PILOT-08RC`。

明确未实现：FieldOperation 详情、SavedView、企业 OIDC/BFF、真实 sandbox（异常队列 query 水合见 M165）。

### M163：Admin 外部审核回执 ExternalReviewReceipt 详情页

已实现：

- 复用已 Implemented `GET /internal/external-review-receipts/{id}`（`evidence.read`）；
- Admin `/external-review-receipts/{id}`；时间线/最近活动白名单；
- Playwright `ADMIN-PILOT-08ER`：厂端回调后核心时间线 → 回执 GET 200。

明确未实现：FieldOperation 详情、SavedView、企业 OIDC/BFF、真实 sandbox。

### M162：Admin 最近活动资源详情深链

已实现：

- 复用 M93 activity-summary 与时间线白名单；工作区「打开最近活动资源」；
- Playwright `ADMIN-PILOT-08AS`：固定 Pilot 工单 → Task GET 200。

明确未实现：关键事件 taxonomy、FieldOperation 详情、SavedView、企业 OIDC/BFF、真实 sandbox。

### M161：Admin 核心时间线资源详情深链

已实现：

- 权威核心时间线旁链（M153 留待项）；白名单扩展 FormSubmission / EvidenceItem / EvidenceSetSnapshot；
- Playwright `ADMIN-PILOT-08CT`：完结后核心时间线 → FormSubmission / Snapshot GET 200。

明确未实现：FieldOperation 详情、SavedView、企业 OIDC/BFF、真实 sandbox。

### M160：Admin 联系尝试 ContactAttempt 详情页

已实现：

- Core OpenAPI 0.75.0 `GET /contact-attempts/{contactAttemptId}`（`appointment.read`；不可变无 ETag）；
- Admin `/contact-attempts/{id}` 只读详情；工作区 AV → 联系详情深链；
- PostgreSQL IT、MVC 安全测试、Playwright `ADMIN-PILOT-08CA`。

明确未实现：FieldOperation 详情、SavedView、企业 OIDC/BFF、真实 sandbox。

### M159：Admin 上门 Visit 详情页

已实现：

- Core OpenAPI 0.74.0 `GET /visits/{visitId}`（`visit.read` + ETag）；
- Admin `/visits/{id}` 只读详情；工作区 AV → 上门详情深链；
- PostgreSQL IT、MVC 安全测试、Playwright `ADMIN-PILOT-08VD`。

明确未实现：ContactAttempt 详情、FieldOperation 详情、SavedView、企业 OIDC/BFF、真实 sandbox。

### M158：授权入站 Envelope 队列

已实现：

- API-06 §6.1 窄接受 `GET /inbound-envelopes`（Core OpenAPI 0.73.0）；
- 默认 `processingStatus=RECEIVED`；稳定倒序游标；仅已绑定 `projectId`；
- Flyway V085 游标索引；PostgreSQL IT + MVC 安全测试；
- Admin `/integration/inbound` 筛选页与详情深链；Playwright `ADMIN-PILOT-08IQ`。

明确未实现：null-project 可见性、原文下载、SavedView、Visit 详情、企业 OIDC/BFF、真实 sandbox。

### M157：Admin 工作区项目与 SLA 任务深链

已实现：

- 工作区概览 `projectId` → 项目详情；`currentTaskSummary.taskId` → 任务详情；
- 工作区/SLA 工作台「打开 SLA 关联任务」→ Task 详情；
- Playwright：项目 GET；工作区与队列 SLA→Task GET。

明确未实现：Visit 独立详情页、SavedView、企业 OIDC/BFF、真实 sandbox。

### M156：Admin 资料项/资料快照详情页

已实现：

- `/evidence-items/{id}`、`/evidence-set-snapshots/{id}` 只读详情页；
- 工作区 FE → 资料项详情；Task 面板 → 资料快照详情；
- Playwright：Snapshot GET（新页签）；完结后 EvidenceItem GET。

明确未实现：Visit 独立详情页、详情页写命令、专用入站队列列表 API、SavedView、真实 sandbox。

### M155：Admin 预约/表单提交详情页

已实现：

- `/appointments/{id}`、`/form-submissions/{id}` 只读详情页；
- 工作区 AV/FE → 预约/表单提交详情深链；保留 M154 Task 旁路；
- Playwright：预约确认后 Appointment GET；完结后 FormSubmission GET。

明确未实现：Visit 独立详情页、详情页写命令、专用入站队列列表 API、SavedView、真实 sandbox。

### M154：Admin 工作区预约上门/表单资料 → Task 旁路深链

已实现：

- 工作区 `APPOINTMENTS_VISITS` / `FORMS_EVIDENCE` → `/tasks/{taskId}`；
- 复用已 Implemented Task 详情（现场面板 / 表单资料面板）；
- Playwright：预约确认后 AV 深链；完结后 FE 深链。

明确未实现：Visit/EvidenceItem 独立详情页、专用入站队列列表 API、SavedView、真实 sandbox。

### M153：Admin 工作区 TIMELINE_AUDIT → 资源详情深链

已实现：

- 工作区按需 `TIMELINE_AUDIT` → 白名单 `resourceType` 详情深链（「打开时间线资源」）；
- 复用已有 Task/Review/Correction/Outbound/Exception/SLA/WorkOrder 详情路由；
- Pilot 种子 `resource_type` 纠偏为 OpenAPI PascalCase；
- Playwright：固定 Pilot 工单从时间线点击 Task 深链。

明确未实现：Appointment/Visit/Form/Evidence 独立详情页、权威时间线表格内嵌链接、专用入站队列列表 API、SavedView、真实 sandbox。

### M152：Admin 工作区 TASKS → 任务详情深链

已实现：

- 工作区按需 `TASKS` 区块 → `/tasks/{taskId}`（「打开区块任务」）；
- 复用已 Implemented Task 详情 GET 与详情页；
- Playwright：固定 Pilot 工单从 TASKS 深链打开任务详情。

明确未实现：专用入站队列列表 API、SavedView、真实 sandbox。

### M151：Admin 目录与 SLA Accepted OpenAPI 筛选补齐

已实现：

- 工单目录 `projectId`；任务目录 `projectId` + `SUCCEEDED`；
- SLA 工作台 `projectId`；项目目录 `activeOn`；
- Playwright 四类筛选 GET 200。

明确未实现：专用入站队列列表 API、SavedView、真实 sandbox。

### M150：Admin 运营异常队列筛选

已实现：

- Operational Exception 队列 UI 绑定 Accepted OpenAPI 筛选：`status` / `severity` / `category` /
  `projectId` / `workOrderId` / `taskId`；
- 默认 `status=OPEN`；省略 status 表示不限（与服务端一致）；
- Playwright：切换 `ACKNOWLEDGED+P1` 查询返回 200。

明确未实现：多 status OR、SavedView、专用入站队列列表 API、真实 sandbox。

### M149：Admin 工作区审核/整改详情深链

已实现：

- 工作区 REVIEWS_CORRECTIONS → `/reviews/{reviewCaseId}` 与 `/corrections/{correctionCaseId}`；
- 复用已 Implemented 详情 GET 与详情页；
- Playwright：驳回后从工作区点击审核与整改深链。

明确未实现：专用入站队列列表 API、SavedView、真实 sandbox。

### M148：Admin 审核/整改队列筛选

已实现：

- Review 队列 UI 绑定 Accepted OpenAPI 筛选：`status` / `origin` / `projectId` / `taskId`（默认 OPEN）；
- Correction 队列 UI 绑定：`status` / `projectId` / `taskId` / `sourceReviewCaseId`（默认 IN_PROGRESS）；
- Playwright：审核 `OPEN+taskId`；整改 `IN_PROGRESS+sourceReviewCaseId`。

明确未实现：多 status OR、SavedView、SLA/assignee 富化、专用入站队列列表 API、真实 sandbox。

### M147：Admin 工作区外发交付详情深链

已实现：

- 工作区 INTEGRATION → `/integration/outbound/{deliveryId}`；
- 复用已 Implemented `GET /outbound-deliveries/{id}` 与详情页；
- Playwright：ACK 后从工作区点击外发深链并断言详情与 `externalOrderCode`。

明确未实现：专用入站队列列表 API、SavedView、真实 sandbox。

### M146：Admin 外发交付队列筛选

已实现：

- Outbound Delivery 队列 UI 绑定 Accepted OpenAPI 单值筛选：`status`、`businessMessageType`、
  `projectId`、`sourceWorkOrderId`、`sourceReviewCaseId`；
- 默认 `status=UNKNOWN`，与后端省略 status 时的契约默认一致；
- Playwright：ACK 后切换 `ACKNOWLEDGED` 筛选仍可见该 delivery。

明确未实现：多 status OR、SavedView、专用入站队列列表 API、真实 sandbox。

### M145：Admin 入站 Envelope/Canonical 详情深链

已实现：

- 工作区 INTEGRATION → `/integration/inbound/{envelopeId}`；
- 复用已 Implemented `GET /inbound-envelopes/{id}` 与 `GET /canonical-messages/{id}`；
- Playwright 断言 Envelope/Canonical/`BYD:INSTALL:` 与回链工单。

明确未实现：专用入站队列列表 API、原文下载、真实 sandbox。

## 5. 下一实施方向

ServiceOS 可靠纵向切片已推进到 **M167**。Admin Pilot 已覆盖工作区区块详情/旁路、预约/表单/资料/上门/联系详情、
核心时间线与最近活动资源深链、外部审核回执详情、审核/整改交叉深链、异常摘要→异常队列 query 水合、工作区审核/整改关联资源深链、Task 面板资源详情深链、项目与 SLA 任务交叉深链、专项队列（含入站 Envelope）与目录筛选、`ADMIN-PILOT-09`；
没有实现完整评分派单引擎、完整 SLA/通知策略、通用队列/SavedView 或整个现场履约平台。

```text
候选下一方向（优先从已确认文档中选择最小可靠切片；勿发明契约）：
1. 正式企业 OIDC/BFF、MFA 与设计系统；SavedView 仍需再接受 API-06 章节；
2. 在接受 ServiceNetwork 状态语义后建立目录与准入/启用/清退生命周期；当前相关文档仍为 Proposed，
   不得猜测状态值或转换规则；
3. 建立 Organization/Region 目录、层级后代与组织到 Project 的权威关系；
4. 在试点确认日历/暂停/预警规则后扩展 BUSINESS 时钟、暂停和升级；
5. 多候选人评分、硬过滤重跑、自动 claim、网点容量联动（需另接受 api/04 / ADR-009 切片）；
6. OCR/CV、GPS 权威距离、二级审批/MFA、报告 GENERATED 资料包；
7. 表达式计算字段、决策表/脚本、草稿冲突与离线合并；
8. 履约事实试算与结算运行时；Admin 投影重建/重放 HTTP（需另接受运维契约）；
9. FieldOperation 详情（API-03 仍为 Proposed，不得猜测读契约）。
```

接手 Agent 必须先检查仓库是否已有更新的里程碑文档、ADR 或提交；在收到明确批准前不得猜测业务策略并实现上述候选项。

## 6. 证据阅读方法

判断某项能力是否完成时，按以下顺序检查：

```text
本文件中的状态
→ 对应 architecture/Mxx 实现文档
→ 对应 testing/Mxx 验收矩阵
→ implementation-traceability-matrix.md
→ OpenAPI / 事件 Schema / Flyway
→ 自动化测试和提交记录
```

只有总体设计文档而没有实现文档和验收证据时，不得标记为 `IMPLEMENTED`。

## 7. 强制维护规则

每次里程碑或已实现范围发生变化，负责该变更的 Agent 必须在同一提交或同一 PR 中同步更新本文件，至少包括：

1. `lastUpdated`；
2. `baselineCommit`，若提交 SHA 在提交前未知，可在 PR 合并后由后续维护提交补齐，变更说明中必须明确；
3. `latestMilestone`；
4. 能力实施总览中的状态、已完成范围、未完成范围和证据；
5. 最近里程碑章节；
6. 下一实施方向；
7. 与 `implementation-traceability-matrix.md`、里程碑实现文档和验收矩阵保持一致。

以下情况视为文档门禁失败：

- 新里程碑标记为 Implemented，但本文件未更新；
- 本文件声称完成，但没有对应代码、迁移、机器契约或测试证据；
- 删除或隐藏“未实现范围”；
- 使用模糊的“基本完成”“差不多完成”替代可验证范围；
- 最新基线和实际仓库进度明显不一致且没有说明。

## 8. 相关入口

- `serviceos-architecture/README.md`
- `serviceos-architecture/docs/implementation-traceability-matrix.md`
- `serviceos-architecture/roadmap/00-mvp-roadmap.md`
- `serviceos-architecture/roadmap/02-m7-application-delivery-plan.md`
- `serviceos-architecture/architecture/55-evidence-invalidate-runtime.md`
- `serviceos-architecture/testing/39-m42-evidence-invalidate-acceptance.md`
- `serviceos-architecture/architecture/54-evidence-task-completion-gate.md`
- `serviceos-architecture/testing/40-m43-dual-input-task-completion-acceptance.md`
- `serviceos-architecture/architecture/85-m72-task-execution-attempt-history.md`
- `serviceos-architecture/testing/69-m72-task-execution-attempt-history-acceptance.md`
- `serviceos-architecture/architecture/86-m73-work-order-core-execution-timeline.md`
- `serviceos-architecture/testing/70-m73-work-order-core-execution-timeline-acceptance.md`
- `serviceos-architecture/architecture/87-m74-work-order-field-ops-timeline.md`
- `serviceos-architecture/testing/71-m74-work-order-field-ops-timeline-acceptance.md`
- `serviceos-architecture/architecture/88-m75-work-order-sla-timeline.md`
- `serviceos-architecture/testing/72-m75-work-order-sla-timeline-acceptance.md`
- `serviceos-architecture/architecture/89-m76-work-order-evidence-review-timeline.md`
- `serviceos-architecture/testing/73-m76-work-order-evidence-review-timeline-acceptance.md`
- `serviceos-architecture/architecture/90-m77-work-order-delivery-exception-timeline.md`
- `serviceos-architecture/testing/74-m77-work-order-delivery-exception-timeline-acceptance.md`
- `serviceos-architecture/architecture/91-m78-work-order-delivery-ack-timeline.md`
- `serviceos-architecture/testing/75-m78-work-order-delivery-ack-timeline-acceptance.md`
- `serviceos-architecture/architecture/67-m54-external-review-affected-target-validation.md`
- `serviceos-architecture/testing/51-m54-external-review-affected-target-validation-acceptance.md`
- `serviceos-architecture/architecture/68-m55-client-review-case-origin-runtime.md`
- `serviceos-architecture/testing/52-m55-client-review-case-origin-acceptance.md`
- `serviceos-architecture/architecture/70-m57-byd-review-callback-runtime.md`
- `serviceos-architecture/testing/54-m57-byd-review-callback-acceptance.md`
- `serviceos-architecture/architecture/71-m58-byd-review-submission-outbound-delivery.md`
- `serviceos-architecture/testing/55-m58-byd-review-submission-outbound-delivery-acceptance.md`
- `serviceos-architecture/architecture/78-m65-project-network-scope-sla-queue.md`
- `serviceos-architecture/testing/62-m65-project-network-scope-sla-queue-acceptance.md`
- `serviceos-architecture/architecture/79-m66-project-scope-relation-revision.md`
- `serviceos-architecture/testing/63-m66-project-scope-relation-revision-acceptance.md`
- `serviceos-architecture/architecture/80-m67-authorized-project-directory-query.md`
- `serviceos-architecture/testing/64-m67-authorized-project-directory-query-acceptance.md`
