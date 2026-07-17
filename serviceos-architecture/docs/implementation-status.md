---
title: ServiceOS 实施状态总览
version: 0.1.0
status: Implemented
lastUpdated: 2026-07-17
baselineCommit: 7681b582c54feb60874256bdf9c94f7faa3c4f07
latestMilestone: M193
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
| 最新实施里程碑 | M193 Admin 最近访问 |
| 基线提交 | `7681b582c54feb60874256bdf9c94f7faa3c4f07` |
| 后端形态 | Java 21 + Spring Boot + Spring Modulith 模块化单体 |
| 当前可构建工程 | `serviceos-backend`、`serviceos-contracts` |
| 前端工程 | `serviceos-admin-web`（Vue+TS+Vite）已纳入 CI 构建，具备开发态 Keycloak PKCE，以及真实只读、Task MANUAL assign-candidates/claim/release、表单/资料/审核/整改/完结、正常补传复审，预约上门、BYD 提审外发 ACK、厂端回调，CPIM 入站→激活→Admin HTTP 人工初派→同单预约上门→表单/资料/驳回整改补传复审/外发/完结（ADMIN-PILOT-09），入站 Envelope 授权队列与详情深链、专项队列与目录/SLA Accepted OpenAPI 筛选，工作区各按需区块详情或 Task 旁路、预约/表单/资料/上门/联系详情页、核心时间线与最近活动资源深链、外部审核回执详情、审核/整改交叉深链、工作区异常摘要→异常队列 query 水合与 handlingTaskId 深链、工作区审核/整改关联资源深链、Task 面板资源详情深链、Canonical Message 独立详情页、专项队列与目录页 query 水合及关联资源深链、外发关联资源与回执入站交叉深链、详情页明文 projectId / 源资源 / 现场与 SLA scope 字段深链、专项队列剩余 Accepted 关联字段深链、QueueTable 可选行内单元格深链、外发 executionTaskId / 快照成员资料项深链，工作区项目与 SLA 任务交叉深链的 PR 阻断 E2E；**M187 Admin 统一用户中心**；**M188 `/me` 导航**；**M189 Admin 个人 SavedView**；**M190 Admin UI Preferences**；**M191 Admin 共享 SavedView**；**M192 Admin 受控全局搜索**；**M193 Admin 最近访问** |
| 数据库 | PostgreSQL + Flyway（当前版本 095 / 97） |
| 契约 | Core OpenAPI 0.85.0 + BYD CPIM OpenAPI 0.3.0 + 外部/事件 JSON Schema（含 project.created@v3、project.scope-relations-revised@v1、recovered/resolved 与 SLA started/breached/met@v1） |

每次完成新里程碑时，Agent 必须更新本节的最新里程碑、基线提交和更新时间。

## 2.1 已接受的实施序列：身份与组织治理

项目负责人已确认将原计划重编号为 M183～M188，连续承接已实现的 Admin Pilot M135～M182。
M183～M188 已完成统一主体目录、企业组织任职、网点/师傅目录、RoleGrant 治理、Admin 统一用户中心
与 Portal `/me` 上下文/导航。Consumer Identity 仍为后续独立 Epic。

| 里程碑 | 状态 | 目标 |
|---|---|---|
| M183 | `IMPLEMENTED` | 统一 Principal、IdentityLink、PersonProfile、Persona 与主体生命周期 |
| M184 | `IMPLEMENTED` | 企业 Organization/OrgUnit/closure、人员任职和 LOCAL/外部权威同步 |
| M185 | `IMPLEMENTED` | 合作组织、ServiceNetwork 人员、TechnicianProfile、网点关系与资质 |
| M186 | `IMPLEMENTED` | Role/Capability/RoleGrant 申请审批撤销、Delegation、职责分离和授权解释 |
| M187 | `IMPLEMENTED` | Admin 统一用户中心与真实 OIDC 治理 E2E |
| M188 | `IMPLEMENTED` | `/me` contexts/capabilities/navigation、多 Persona 与三 Portal 上下文 |

正式事实源：

- [M183～M188 交付计划](../roadmap/03-identity-organization-governance-delivery-plan.md)
- [程序级验收矩阵](../testing/identity-organization-governance-program-acceptance.md)
- [Agent 工作清单](../roadmap/04-identity-organization-governance-agent-worklist.md)

Consumer Identity/CustomerProfile 是身份治理序列之后的已接受后续 Epic；在登录渠道、隐私同意、客户主数据和注销保留策略确认前不分配里程碑，也不得宣称已实现。

## 3. 能力实施总览

| 领域 | 能力 | 状态 | 已完成范围 | 主要未完成范围 | 最近证据 |
|---|---|---|---|---|---|
| 工程基础 | 构建、测试、契约、可观测性、容器发布 | `IMPLEMENTED` | Maven、PostgreSQL IT、契约门禁、Trace/指标、单镜像迁移和回滚演练 | 正式 K8s、多故障域、PITR、SBOM/签名、正式 Secret Manager | M8～M14 |
| 身份授权 | OIDC/JWT、Capability、Tenant/Project/REGION/NETWORK Scope、拒绝审计 | `IMPLEMENTED` | 后端认证授权和范围校验基线；实时 TENANT/PROJECT/REGION/NETWORK 集合；Project 有效期关系、整组修订与授权目录读取 | 组织关系、Region 层级后代、计划修订/审批、正式企业 IdP、完整组织治理 UI | M9、M63～M67 |
| 统一主体目录 | Principal、IdentityLink、PersonProfile、Persona 与生命周期 | `IMPLEMENTED` | 稳定内部 Principal；受控并发 JIT；多 IdentityLink/Persona；Profile；启停实时失权；安全目录与敏感身份分权查询；幂等、If-Match、审计和 PostgreSQL 不可变事实 | 身份解绑、密码管理、身份缓存与跨服务身份事件；网点、授权治理和 Portal 上下文由 M185～M188 承接 | M183 |
| 企业组织目录 | Organization、OrgUnit、closure、任职与主数据同步 | `IMPLEMENTED` | 独立 `organization` 模块；closure；主职/兼职/负责人；LOCAL/EXTERNAL_AUTHORITATIVE；同步批次幂等与乱序；离职停用/撤权/待重分配；治理 API；Admin 组织页（M187） | 正式 HR Connector、双向回写、ORGANIZATION DataScope | M184、M187 |
| 网点人员与师傅身份 | NetworkMembership、TechnicianProfile、网点关系与资质 | `IMPLEMENTED` | 独立 `network` 模块；PartnerOrganization/ServiceNetwork；成员邀请；TechnicianProfile/多网点关系；资质只追加审核；可接单查询；清退/停用 clearance 与 ACTIVE 派单影响摘要；Admin 网点/师傅页（M187） | Coverage/Capability 地理硬过滤、离线工作包回收、自动改派、Network Portal UI | M185、M187 |
| 角色与授权治理 | Role/Capability/RoleGrant 管理、审批、撤销与 Delegation | `IMPLEMENTED` | 扩展 `authorization`：角色/能力目录、申请/审批/拒绝/撤销、Delegation、SoD 与可授予范围、DENY 优先、grant generation、授权解释与治理 HTTP；Admin 角色/授权页（M187）；`/me*` 上下文与导航（M188） | MFA obligation 执行器 | M186～M188 |
| 统一用户中心 | Admin 用户、组织、网点人员、师傅、角色和授权治理 | `IMPLEMENTED` | 目录选择器、分区详情、影响面板、If-Match 写流、EXTERNAL 只读徽章、Capability 探测导航、真实 Keycloak PKCE E2E（含低权限深链）；M188 起导航改消费 `/me/navigation` | 正式企业 IdP / HR Connector | M187、M188 |
| Portal 上下文与导航 | `/me`、contexts、capabilities、navigation 与多 Persona | `IMPLEMENTED` | 服务端上下文；代码 Page Registry + V090 覆盖；contextVersion 失权；Admin 消费与 Network/Technician stub；CONSUMER 不暴露入口 | 完整 Network/Technician 产品 UI；Consumer Identity | M188 |
| Admin 个人 SavedView | 个人筛选视图 CRUD 与 Admin 目录/队列应用 | `IMPLEMENTED` | API-06 §8 个人切片；`rdm_saved_view`；受控 filter AST；Admin Task/WorkOrder/Correction SavedViewBar；不授予页面能力 | Network/Technician 偏好、设计系统级组件 | M189 |
| Admin UI Preference | 个人展示偏好 CRUD 与 Admin Web 应用 | `IMPLEMENTED` | API-06 §9 Admin 切片；`rdm_ui_preference`；键白名单；主题/密度/减少动画；可选默认 SavedView 绑定 | 共享偏好、Network/Technician Portal、设计系统级主题引擎 | M190 |
| Admin 共享 SavedView | 角色/租户共享查询定义与列表合并 | `IMPLEMENTED` | API-06 §8 共享切片；`visibility` ROLE/TENANT；`preference.shareSavedView`；列表合并可见共享；Share≠数据授权 | ORGANIZATION 组织树共享、Network/Technician SavedView、共享 UI Preference | M191 |
| Admin 受控全局搜索 | Admin 授权查询 fan-in 搜索 | `IMPLEMENTED` | API-06 §7 Admin 切片；`search.read` + type 读能力降级；WO/EXTERNAL/NETWORK/TECHNICIAN；无索引平台；Admin Search 页 | `search_document` 索引、VEHICLE/CHARGER、Network/Technician Portal 搜索 | M192 |
| Admin 最近访问 | 个人最近访问 touch/list 与读时重鉴权 | `IMPLEMENTED` | API-06 §3 Admin 切片；`rdm_recent_resource`；WO/TASK/PROJECT/NETWORK/TECHNICIAN；失权省略；AppShell Recent | notifications、application-context、Network/Technician Portal 最近访问 | M193 |
| Consumer Identity | CustomerProfile、用户资源关系和 C 端身份 | `ACCEPTED` | Principal/IdentityLink/API Schema 已预留 Consumer Persona | 身份治理序列之后的独立 Epic；待登录、隐私、客户主数据与注销策略确认；不得宣称已实现 | 后续正式 Epic |
| 项目治理 | Project 核心事实、范围关系与授权目录 | `PARTIAL` | 项目创建；REGION/NETWORK 当前关系整组修订和不可变历史；`project.read` 授权目录、详情及历史查询 | owners、品牌/服务产品/配置绑定、生命周期、计划修订审批、目录治理 UI | M8、M64～M67 |
| 可靠消息 | Inbox、Outbox、Worker claim/lease/retry | `IMPLEMENTED` | 本地可靠发布消费、恢复和人工接管基础 | 正式 Broker 和跨服务运行 | M9～M10 |
| 配置中心 | 不可变配置资产、Bundle 发布和版本锁定 | `PARTIAL` | FORM、EVIDENCE、SLA v1 资产发布基础；工单/任务冻结引用；SERVICEOS_EXPR_V1 布尔/类型比较子集；FORM/EVIDENCE 字段及 WORKFLOW/SLA 依赖闭包 | 决策表/公式/脚本、完整审批和通用依赖图 | M16、M33、M36、M52～M53、M61 |
| 外部接入 | BYD CPIM V7.3.1 入站、提审与审核回调 | `PARTIAL` | 协议日期验签、防重放、私有原文、Envelope/Canonical、工单创建；显式审核路由与逐订单回调；不可变 OutboundDelivery/Attempt/Acknowledgement、Task 可靠执行、UNKNOWN 人工接管与授权人工重发；重发严格 ACK 后发布恢复事实；交付创建/确认/恢复/重发请求与异常确认已并入工单时间线；授权跨项目外发交付队列与入站 Envelope 队列 | 其他 CPIM 消息、人工标记已送达/放弃、通用 Connector、生产凭据/对象存储和真实 sandbox、null-project 入站可见性 | M16、M56～M60、M77～M79、M99、M158 |
| 工单 | WorkOrder 接收、激活、履约完成与授权工作区投影 | `PARTIAL` | 权威工单、工作流启动、跨阶段和 END 完结；授权目录、非 PII 详情、Stage/Task 执行骨架及核心执行+现场履约时间线 | 完整取消、暂停、恢复、客户敏感详情审计、跨域完整时间线/动作与全部业务分支 | M16～M19、M68～M69、M73～M74 |
| 工作流 | 线性 Stage/Task 运行时 | `PARTIAL` | 精确版本启动、线性推进、唯一跨阶段推进、完成事件；节点 `slaRef` 传递；授权 Workflow/Stage 当前投影 | 并行/汇聚网关、流程条件表达式、Node/Attempt 历史和复杂流程语义 | M17～M19、M61、M69 |
| 人工任务与执行历史 | claim/start/complete、责任、执行保护与授权任务读取 | `IMPLEMENTED` | 人工命令、候选领取、唯一责任、release/reclaim、执行保护；表单/资料完成门禁；授权队列/详情、allowed-actions、自动 Attempt 历史及工单内核心 Task 生命周期与指派/Guard/人工接管时间线 | block/retry/cancel 等其他动作、Workflow Node 历史、跨工单/跨域完整历史和 Review 完成条件 | M20～M23、M35、M41、M43、M69～M73、M81 |
| 应用只读投影 | 工作区、队列、时间线和投影运行时 | `PARTIAL` | 独立 readmodel 模块；核心执行、现场履约、SLA、资料/审核/整改（含外部回执与条件 KEEP/INVALIDATE 处置）、外发交付全链路、异常确认/闭环、ServiceAssignment 与 Task 指派/Guard/人工接管 Inbox 投影；授权时间线与稳定分页及最近活动摘要；时间线 checkpoint/dead letter/generation 重建与 FRESH/LAGGING/UNKNOWN/REBUILDING freshness；definition 登记、dead letter 幂等重放与旧/孤儿 generation 清理；工单工作区顶层实时组合、当前 ACTIVE 服务责任摘要与 TASKS/TIMELINE_AUDIT/APPOINTMENTS_VISITS（含联系尝试）/FORMS_EVIDENCE（含提交与资料项安全元数据）/REVIEWS_CORRECTIONS（含 CLIENT/重开血缘）/INTEGRATION 按需区块（敏感字段最小化；缺权次级区块降级）；授权跨项目 ReviewCase/CorrectionCase/OutboundDelivery/InboundEnvelope 专项队列；Admin 个人 SavedView（M189）、UI Preference（M190）、共享 SavedView（M191）、受控全局搜索 fan-in（M192）与最近访问（M193） | 试算合并、revision/slots 技术噪声、表单值与资料版本详情、FACTS_CALCULATIONS、完整事件 taxonomy/过滤、通用 work-queues、共享偏好、`search_document` 索引平台、多投影平台、Broker offset、Portal、Admin 重建/重放 HTTP | M73～M99、M158、M189～M193 |
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
| Admin Portal | 总部运营后台 | `PARTIAL` | M101～M182：队列/任务/SLA/异常/入站/外发/工单/项目目录、工作区、allowed-actions；CI 阻断构建；开发态 Keycloak PKCE；真实只读与写链路 PR 阻断 E2E；M187～M188 用户中心与 `/me` 导航；**M189 个人 SavedView**；**M190 UI Preferences**；**M191 共享 SavedView**；**M192 受控全局搜索**；**M193 最近访问** | 设计系统、共享偏好、正式企业 OIDC/BFF、生产对象存储/专业扫描、评分/硬过滤派单与 ServiceNetwork 生命周期 | M7 设计、M101～M182、M187～M193、Admin 试点基线 |
| Network Portal | 网点协作端 | `PROPOSED` | 页面和跨端协作规格 | 前端代码和 E2E | M7 设计 |
| Technician App | 师傅移动端 | `PROPOSED` | 弱网、离线工作包、上传队列和页面规格 | 移动端工程、真机和离线运行时 | M7 设计 |
| External Portal | 用户/车企受控页面 | `PROPOSED` | 最小边界规划 | 二期页面和工程实现 | M7 设计 |

## 4. 里程碑历史摘要

各里程碑的「已实现 / 明确未实现」摘要已迁移至 [implementation-status-archive.md](implementation-status-archive.md)（M33～M182）；一行式里程碑索引见 [milestone-index.md](milestone-index.md)；权威范围以对应 `architecture/Mxx` 实现文档和 `testing/Mxx` 验收矩阵为准。

该 archive 冻结保存拆分前的历史叙事，不再追加新里程碑摘要。新里程碑以实现文档、验收矩阵、追踪矩阵和本文件当前状态为权威，并重新生成索引。

## 5. 下一实施方向

ServiceOS 可靠纵向切片已推进到 **M193**。身份治理 M183～M188、Admin 个人/共享 SavedView、Admin UI
Preference、受控全局搜索与最近访问已交付。没有实现 `search_document` 索引平台、VEHICLE/CHARGER 搜索、
共享 UI Preference、notifications、Consumer Identity、正式 HR Connector、ORGANIZATION DataScope，
也没有完整 Network/Technician 产品 UI。

```text
候选下一方向（优先从已确认文档中选择最小可靠切片；勿发明契约）：
1. Consumer Identity/CustomerProfile Epic（仍为 ACCEPTED/deferred；登录/隐私/主数据确认前不分配里程碑）；
2. 在试点确认日历/暂停/预警规则后扩展 BUSINESS 时钟、暂停和升级；
3. 多候选人评分、硬过滤重跑、自动 claim、网点容量联动（需另接受 api/04 / ADR-009 切片）；
4. OCR/CV、GPS 权威距离、二级审批/MFA、报告 GENERATED 资料包；
5. 表达式计算字段、决策表/脚本、草稿冲突与离线合并；
6. FieldOperation 详情（API-03 仍为 Proposed，不得猜测读契约）；
7. 正式企业门户设计系统、跨 Portal UI Preference / ORGANIZATION 共享 SavedView / search_document 索引 / notifications（需再接受 API-06/DATA-06 其余章节）；完整 Network/Technician Portal UI。
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

定位相关里程碑文档时，先用 [milestone-index.md](milestone-index.md) 和 [agent-navigation.md](agent-navigation.md) 确定最小阅读集，不批量通读 `architecture/` 目录。

## 7. 强制维护规则

每次里程碑或已实现范围发生变化，负责该变更的 Agent 必须在同一提交或同一 PR 中同步更新本文件，至少包括：

1. `lastUpdated`；
2. `baselineCommit`，若提交 SHA 在提交前未知，可在 PR 合并后由后续维护提交补齐，变更说明中必须明确；
3. `latestMilestone`；
4. 能力实施总览中的状态、已完成范围、未完成范围和证据；
5. 运行 `bash scripts/generate-milestone-index.sh`，从权威实现文档重新生成
   [milestone-index.md](milestone-index.md)；历史 archive 保持冻结，不再复制新里程碑摘要；
6. 下一实施方向；
7. 与 `implementation-traceability-matrix.md`、里程碑实现文档和验收矩阵保持一致。

以下情况视为文档门禁失败：

- 新里程碑标记为 Implemented，但本文件未更新；
- 本文件声称完成，但没有对应代码、迁移、机器契约或测试证据；
- 删除或隐藏“未实现范围”；
- 使用模糊的“基本完成”“差不多完成”替代可验证范围；
- 最新基线和实际仓库进度明显不一致且没有说明。

## 8. 相关入口

- `serviceos-architecture/docs/agent-navigation.md`（任务类型 → 最小必读路由表）
- `serviceos-architecture/docs/milestone-index.md`（一行一里程碑，脚本生成）
- `serviceos-architecture/docs/milestone-playbook.md`（里程碑标准执行手册）
- `serviceos-architecture/docs/implementation-status-archive.md`（M33～M182 里程碑历史摘要）
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
