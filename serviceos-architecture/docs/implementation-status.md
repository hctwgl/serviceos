---
title: ServiceOS 实施状态总览
version: 0.1.0
status: Implemented
lastUpdated: 2026-07-16
baselineCommit: 7ac95ae
latestMilestone: M69
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
| 最新实施里程碑 | M69 授权工单执行工作区投影 |
| 基线提交 | `7ac95ae`（M69 功能提交后立即回填） |
| 后端形态 | Java 21 + Spring Boot + Spring Modulith 模块化单体 |
| 当前可构建工程 | `serviceos-backend`、`serviceos-contracts` |
| 前端工程 | 尚未建立；已有 Admin、Network、Technician 产品与交互规格 |
| 数据库 | PostgreSQL + Flyway（当前版本 069 / 71） |
| 契约 | Core OpenAPI 0.40.0 + BYD CPIM OpenAPI 0.3.0 + 外部/事件 JSON Schema（含 project.created@v3、project.scope-relations-revised@v1、recovered/resolved 与 SLA started/breached/met@v1） |

每次完成新里程碑时，Agent 必须更新本节的最新里程碑、基线提交和更新时间。

## 3. 能力实施总览

| 领域 | 能力 | 状态 | 已完成范围 | 主要未完成范围 | 最近证据 |
|---|---|---|---|---|---|
| 工程基础 | 构建、测试、契约、可观测性、容器发布 | `IMPLEMENTED` | Maven、PostgreSQL IT、契约门禁、Trace/指标、单镜像迁移和回滚演练 | 正式 K8s、多故障域、PITR、SBOM/签名、正式 Secret Manager | M8～M14 |
| 身份授权 | OIDC/JWT、Capability、Tenant/Project/REGION/NETWORK Scope、拒绝审计 | `IMPLEMENTED` | 后端认证授权和范围校验基线；实时 TENANT/PROJECT/REGION/NETWORK 集合；Project 有效期关系、整组修订与授权目录读取 | 组织关系、Region 层级后代、计划修订/审批、正式企业 IdP、完整组织治理 UI | M9、M63～M67 |
| 项目治理 | Project 核心事实、范围关系与授权目录 | `PARTIAL` | 项目创建；REGION/NETWORK 当前关系整组修订和不可变历史；`project.read` 授权目录、详情及历史查询 | owners、品牌/服务产品/配置绑定、生命周期、计划修订审批、目录治理 UI | M8、M64～M67 |
| 可靠消息 | Inbox、Outbox、Worker claim/lease/retry | `IMPLEMENTED` | 本地可靠发布消费、恢复和人工接管基础 | 正式 Broker 和跨服务运行 | M9～M10 |
| 配置中心 | 不可变配置资产、Bundle 发布和版本锁定 | `PARTIAL` | FORM、EVIDENCE、SLA v1 资产发布基础；工单/任务冻结引用；SERVICEOS_EXPR_V1 布尔/类型比较子集；FORM/EVIDENCE 字段及 WORKFLOW/SLA 依赖闭包 | 决策表/公式/脚本、完整审批和通用依赖图 | M16、M33、M36、M52～M53、M61 |
| 外部接入 | BYD CPIM V7.3.1 入站、提审与审核回调 | `PARTIAL` | 协议日期验签、防重放、私有原文、Envelope/Canonical、工单创建；显式审核路由与逐订单回调；不可变 OutboundDelivery/Attempt/Acknowledgement、Task 可靠执行、UNKNOWN 人工接管与授权人工重发；重发严格 ACK 后发布恢复事实 | 其他 CPIM 消息、人工标记已送达/放弃、通用 Connector、生产凭据/对象存储和真实 sandbox | M16、M56～M60 |
| 工单 | WorkOrder 接收、激活、履约完成与授权工作区投影 | `PARTIAL` | 权威工单、工作流启动、跨阶段和 END 完结；授权目录、非 PII 详情及 Stage/Task 执行骨架 | 完整取消、暂停、恢复、客户敏感详情审计、时间线/动作与全部业务分支 | M16～M19、M68～M69 |
| 工作流 | 线性 Stage/Task 运行时 | `PARTIAL` | 精确版本启动、线性推进、唯一跨阶段推进、完成事件；节点 `slaRef` 传递；授权 Workflow/Stage 当前投影 | 并行/汇聚网关、流程条件表达式、Node/Attempt 历史和复杂流程语义 | M17～M19、M61、M69 |
| 人工任务 | claim/start/complete、责任、执行保护与工单任务摘要 | `IMPLEMENTED` | 人工命令、候选领取、唯一责任、release/reclaim、执行保护；表单/资料完成门禁；授权工单 Task 摘要分页 | 独立 Task 队列/详情、动态动作、Node/Attempt 历史和 Review 完成条件 | M20～M23、M35、M41、M43、M69 |
| 服务分配 | 网点分配、容量、改派 Saga、超时恢复 | `IMPLEMENTED` | ServiceAssignment、容量权威、改派、终止、对账和自动恢复 | 完整策略评分、全部异常分支和 UI | M24～M28 |
| 运营异常 | 异常工作台基础 | `PARTIAL` | 异常记录和恢复入口；M58 将外发 UNKNOWN 与 Task 最终人工事件汇入 OperationalException + HUMAN Task；M59 提供高风险人工重发事实；M60 在严格 ACK 后幂等闭环对应异常并处理事件乱序 | 人工标记已送达/放弃、其他异常类型自动闭环、完整通知、运营中心前端和跨域异常目录 | M29、M58～M60 |
| 预约 | 预约修订、联系终态动作 | `PARTIAL` | Revision、并发和终态动作基础 | 用户确认渠道、完整日程和跨端协作 | M30～M31 |
| 现场作业 | Visit 生命周期 | `PARTIAL` | Visit 运行时基础 | GPS 策略、完整现场提交、离线同步和师傅端 | M32 |
| 动态表单 | 资产、冻结版本、不可变提交和 Task 完成门禁 | `PARTIAL` | 固定/条件 required、visible 与布尔 validation rule，基础类型校验、精确版本提交和完成引用 | 复杂 validator、计算字段、草稿、冲突、更正和审核 | M33～M35、M53 |
| 资料 Evidence | 资产、槽位、Item/Revision、机器校验、Snapshot、完成门禁、作废、Review、Correction | `PARTIAL` | 固定/条件槽位、VALIDATED 表单触发只追加重解析、槽位世代/lineage、REVIEW_REQUIRED 与显式 KEEP/INVALIDATE、安全文件联动、Snapshot/完成门禁及审核整改链路 | OCR/CV、GPS 权威距离、长期归档 | M36～M53 |
| 安全文件 | Begin/Finalize/隔离/扫描/授权下载/作废 | `IMPLEMENTED` | 独立安全文件生命周期；Evidence 编排 Begin/Finalize/Invalidate 联动 | 正式对象存储、专业扫描服务、物理删除 | M11、M38、M46 |
| 审核整改 | ReviewCase、ReviewDecision、CorrectionCase | `PARTIAL` | Review + Correction + 整改 Task + 强制通过/重开 + 车企回执 + WAIVED；CLIENT Case 来源、批次/mapping 冻结；交付明确成功后自动创建 CLIENT Case/Route，UNKNOWN 可授权人工重发并在严格 ACK 后闭环异常 | 多候选人策略、前端、人工标记已送达/放弃、自动 Evidence target 映射 | M44～M60 |
| SLA | 时钟、预警、升级 | `PARTIAL` | Task `TASK_CREATED→TASK_COMPLETED` ELAPSED 时钟；显式策略版本/摘要锁定；TARGET_DUE 对账；RUNNING/BREACHED/MET/MET_LATE；Inbox/Outbox 与不可变 segment/milestone；`sla.read` + 实时 TENANT/PROJECT/REGION/NETWORK 授权集合的跨项目工作台、工单时间线与详情查询；关系修订使旧游标失败关闭 | BUSINESS 日历、暂停/恢复、免责/重算、预警/升级/通知、其他 subject、组织关系、Portal 前端、考核结算 | M61～M66 |
| 通知 | 通知与运营异常中心 | `PROPOSED` | 已有总体设计 | 通知通道、模板、可靠发送和 UI | `architecture/14-*` |
| 履约事实与试算 | 事实提取和双向试算 | `PROPOSED` | 已有设计、API 和数据规划 | 运行时、投影和前端工作区 | M5 设计 |
| 对账结算 | 对账、结算、争议与调整 | `PROPOSED` | 已有边界设计 | 正式运行时和页面 | `architecture/16-*` |
| Admin Portal | 总部运营后台 | `PROPOSED` | 信息架构、Page ID、路由、权限和页面规格 | 前端代码、设计系统实现和 E2E | M7 设计 |
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

明确未实现：完整时间线、Node/Attempt 历史、允许动作、Task 独立队列/详情、客户敏感信息、Portal。

## 5. 下一实施方向

ServiceOS 可靠纵向切片已推进到 **M69**。M61～M69 只实现显式 Task ELAPSED 时钟、其安全授权只读投影、
Project REGION/NETWORK 关系即时整组修订、授权项目目录/历史，以及工单目录/详情/Stage/Task 执行骨架，
没有猜测项目默认时长、日历、暂停或升级策略，也没有实现完整 SLA/通知或整个现场履约平台。

```text
候选下一方向（优先从已确认文档中选择最小可靠切片）：
1. 在接受 ServiceNetwork 状态语义后建立目录与准入/启用/清退生命周期；当前相关文档仍为 Proposed，
   不得猜测状态值或转换规则；
2. 建立 Organization/Region 目录、层级后代与组织到 Project 的权威关系；
3. 在试点确认日历/暂停/预警规则后扩展 BUSINESS 时钟、暂停和升级；
4. 多候选人评分、自动 claim、网点容量联动；
5. OCR/CV、GPS 权威距离、二级审批/MFA、报告 GENERATED 资料包；
6. 表达式计算字段、决策表/脚本、草稿冲突与离线合并；
7. Admin/Network/Technician Portal、履约事实试算与结算运行时。
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
