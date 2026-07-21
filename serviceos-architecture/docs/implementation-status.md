---
title: ServiceOS 实施状态总览
version: 0.1.0
status: Implemented
lastUpdated: 2026-07-21
baselineCommit: "9a05be9b9e22be216542260f0979d85ee2cf2bab"
latestMilestone: M424
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
| 最新实施里程碑 | M424 Network 工单工作区脱敏客户联系摘要 |
| 基线提交 | `9a05be9b9e22be216542260f0979d85ee2cf2bab`（历史基线；合入 master 后回填） |
| 后端形态 | Java 21 + Spring Boot + Spring Modulith 模块化单体 |
| 当前可构建工程 | `serviceos-backend`、`serviceos-contracts`、`@serviceos/web-core`、`ServiceOSIOSCore`、独立且可部署的 `serviceos-network-web` 与 `serviceos-technician-web`、Swift 6 `TechnicianIOSFoundation`，以及已在 iPhone 17 Pro Simulator 安装启动、实跑 XCTest/XCUITest、形成 Production arm64 archive/dSYM，并接入当前任务、在线 Visit、冻结基础表单、前台 Evidence 采集上传、Snapshot/Task 完成与多轮资料整改的原生 `TechnicianIOS` SwiftUI App；由同一 Core OpenAPI 生成并经独立消费者门禁验证的 `@serviceos/core-client` 与 `ServiceOSCoreClient` |
| 前端工程 | `serviceos-admin-web` 独立承载总部运营；**M370～M377** 完成设计系统 Presenter、产品化 AppShell（ScopeBar/Freshness/诊断抽屉）、标准页模板、工单中心/详情、项目详情与主导航页壳迁移及视觉关闭；仍消费服务端 Navigation 与 allowed-actions；M256 后 Network 正式产品由独立 `serviceos-network-web` 承载，M257 后 Technician 正式产品由独立移动优先 `serviceos-technician-web` 承载；两套独立 Web 均实际接入共享 Core、OIDC PKCE、服务端 Context/Capability/导航、Playwright 回归和独立容器镜像 |
| 数据库 | PostgreSQL + Flyway（当前版本 **145**；M420 师傅客户端种类声明事件；M419 授权拒绝 actor 索引；M414 品牌目录/省级骨架；M406 车企/行政区目录；M403 登录事件；M402 `identity.register`；M401 关注项目） |
| 契约 | Core OpenAPI **1.0.90** + BYD CPIM OpenAPI 0.3.0 + 外部/事件 JSON Schema（含 Network/Admin 工单工作区脱敏客户联系、履约使用中工单摘要、师傅列表开放任务/资质摘要、CLIENT_KINDS 时间线、authorization-denials、师傅档案/服务关系/网点任职 change-timeline、跨聚合 change-timeline、主数据治理、预约日历、分配候选推荐解释、工作台今日预约时间轴、分配候选距离亲和、关注项目角标、`/project-clients`、`/region-catalog`、`org-memberships`、`recent-logins`、用户目录等既有契约） |

每次完成新里程碑时，Agent 必须更新本节的最新里程碑、基线提交和更新时间。

`baselineCommit` 统一指向包含 `latestMilestone` 全部最终证据、且已经进入 `master` 的合并提交；里程碑分支中的中间实现提交只记录在对应实现文档和验收证据中，不再作为本文件的当前基线。

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
| 工程基础 | 构建、测试、契约、可观测性、容器发布 | `IMPLEMENTED` | Maven、PostgreSQL IT、契约门禁、Trace/指标、单镜像迁移和回滚演练；Track A 同源 TS/Swift Client、Token、Web/iOS Core、身份注册和有界客户端元数据；M256/M257 独立 Web；M258～M260 iOS Foundation/App/Simulator；M261 Production archive/dSYM、隐私/AppIcon 与签名失败关闭；**M356～M363** 客户端能力发布门禁、运行时拒单、定向发布、Feed/详情头拒单、整改路径对齐、整改列表预检与领取/启动硬拒 | 签名真机/TestFlight、生产 IdP/BFF、正式 K8s、多故障域、PITR、远端制品发布/SBOM/签名、正式 Secret Manager | M8～M14、M247～M261、M356～M363 |
| 身份授权 | OIDC/JWT、Capability、Tenant/Project/REGION/NETWORK Scope、拒绝审计 | `IMPLEMENTED` | 后端认证授权和范围校验基线；实时 TENANT/PROJECT/REGION/NETWORK 集合；Project 有效期关系、整组修订与授权目录读取；M183～M188 已补齐 Principal、企业组织任职、RoleGrant 治理、统一用户中心与 `/me` 上下文/导航 | Region 层级后代、Project 计划修订/审批、正式企业 IdP、HR Connector、ORGANIZATION DataScope、MFA obligation 执行器 | M9、M63～M67、M183～M188 |
| 统一主体目录 | Principal、IdentityLink、PersonProfile、Persona 与生命周期 | `IMPLEMENTED` | 稳定内部 Principal；受控并发 JIT；多 IdentityLink/Persona；Profile；启停实时失权；安全目录与敏感身份分权查询；幂等、If-Match、审计和 PostgreSQL 不可变事实 | 身份解绑、密码管理、身份缓存与跨服务身份事件；网点、授权治理和 Portal 上下文由 M185～M188 承接 | M183 |
| 企业组织目录 | Organization、OrgUnit、closure、任职与主数据同步 | `IMPLEMENTED` | 独立 `organization` 模块；closure；主职/兼职/负责人；LOCAL/EXTERNAL_AUTHORITATIVE；同步批次幂等与乱序；离职停用/撤权/待重分配；治理 API；Admin 组织页（M187） | 正式 HR Connector、双向回写、ORGANIZATION DataScope | M184、M187 |
| 网点人员与师傅身份 | NetworkMembership、TechnicianProfile、网点关系与资质 | `IMPLEMENTED` | 独立 `network` 模块；PartnerOrganization/ServiceNetwork；成员邀请；TechnicianProfile/多网点关系；资质只追加审核；可接单查询；清退/停用 clearance 与 ACTIVE 派单影响摘要；Admin 网点/师傅页（M187）；Network Portal 师傅只读列表（M194）；Network Portal 师傅关系绑定/终止与资质 PENDING 提交（M204）；Network Portal 本网点资质只读列表（M205）；Network Portal 师傅关系只读列表含 version（M206）；**M366** 师傅 `supportedClientKinds` 声明（V134 + declare API） | Coverage/Capability 地理硬过滤、离线工作包回收、自动改派、Portal decide/FileObject | M185、M187、M194、M204～M206、M366、M367 |
| 角色与授权治理 | Role/Capability/RoleGrant 管理、审批、撤销与 Delegation | `IMPLEMENTED` | 扩展 `authorization`：角色/能力目录、申请/审批/拒绝/撤销、Delegation、SoD 与可授予范围、DENY 优先、grant generation、授权解释与治理 HTTP；Admin 角色/授权页（M187）；`/me*` 上下文与导航（M188） | MFA obligation 执行器 | M186～M188 |
| 统一用户中心 | Admin 用户、组织、网点人员、师傅、角色和授权治理 | `IMPLEMENTED` | 目录选择器、分区详情、影响面板、If-Match 写流、EXTERNAL 只读徽章、Capability 探测导航、真实 Keycloak PKCE E2E（含低权限深链）；M188 起导航改消费 `/me/navigation` | 正式企业 IdP / HR Connector | M187、M188 |
| Portal 上下文与导航 | `/me`、contexts、capabilities、navigation 与多 Persona | `IMPLEMENTED` | 服务端上下文；代码 Page Registry + V090 覆盖；contextVersion 失权；Admin 消费；Network Portal 只读消费（M194）；Technician Feed 消费（M195）；CONSUMER 不暴露入口 | 完整 Technician App；Consumer Identity | M188、M194、M195 |
| Admin 个人 SavedView | 个人筛选视图 CRUD 与 Admin 目录/队列应用 | `IMPLEMENTED` | API-06 §8 个人切片；`rdm_saved_view`；受控 filter AST；Admin Task/WorkOrder/Correction SavedViewBar；不授予页面能力 | Network/Technician 偏好、设计系统级组件 | M189 |
| Admin UI Preference | 个人展示偏好 CRUD 与 Admin Web 应用 | `IMPLEMENTED` | API-06 §9 Admin 切片；`rdm_ui_preference`；键白名单；主题/密度/减少动画；可选默认 SavedView 绑定 | 共享偏好、Network/Technician Portal、设计系统级主题引擎 | M190 |
| Admin 共享 SavedView | 角色/租户共享查询定义与列表合并 | `IMPLEMENTED` | API-06 §8 共享切片；`visibility` ROLE/TENANT；`preference.shareSavedView`；列表合并可见共享；Share≠数据授权 | ORGANIZATION 组织树共享、Network/Technician SavedView、共享 UI Preference | M191 |
| Admin 受控全局搜索 | Admin 授权查询 fan-in 搜索 | `IMPLEMENTED` | API-06 §7 Admin 切片；`search.read` + type 读能力降级；WO/EXTERNAL/NETWORK/TECHNICIAN；无索引平台；Admin Search 页 | `search_document` 索引、VEHICLE/CHARGER、Network/Technician Portal 搜索 | M192 |
| Admin 最近访问 | 个人最近访问 touch/list 与读时重鉴权 | `IMPLEMENTED` | API-06 §3 Admin 切片；`rdm_recent_resource`；WO/TASK/PROJECT/NETWORK/TECHNICIAN；失权省略；AppShell Recent | notifications、application-context、Network/Technician Portal 最近访问 | M193 |
| Network Portal 只读查询 | 网点协作只读列表与工作台 | `IMPLEMENTED` | API-06 §10 子集；`X-Network-Context`；ACTIVE NETWORK assignment 工单/任务；ACTIVE 师傅；capacity；M256 起由独立 Network Web 正式承载 | 完整 product/03、评分引擎；其余写命令 | M194、M256 |
| Network Portal 指派师傅 | 网点协作写命令（指派） | `IMPLEMENTED` | `POST .../tasks/{taskId}:assign-technician`；强制 networkAssigneeId；委托 ManualAssign；`networkPortal.assignTechnician`；Admin Web 表单 | 评分/硬过滤、资料 Network 写（改派见 M200） | M196 |
| Network Portal 改派师傅 | 网点协作写命令（改派） | `IMPLEMENTED` | `POST .../tasks/{taskId}:reassign-technician`；supersedes ACTIVE TECHNICIAN；`networkPortal.reassignTechnician`；委托 ManualReassign；Admin Web 动作 | 跨网点改派、资料补传、评分 | M200 |
| Network Portal 资料代补 | 网点协作写命令（onBehalf） | `IMPLEMENTED` | begin/finalize on-behalf + correction resubmit；`evidence.submitOnBehalf`；CaptureMetadata 服务端写入；Page Registry `NETWORK.EVIDENCE.SUPPLEMENT`；Admin Web 控件；M368 `NETWORK_WEB` 能力门禁（ADR-089） | 槽位 allowOnBehalf、表单代改、Visit | M201、M368 |
| Network Portal 整改队列 | 网点协作只读整改发现面 | `IMPLEMENTED` | list/get correction-cases；复用 `evidence.read` NETWORK；Page Registry `NETWORK.CORRECTION.QUEUE`；Admin Web `/network-portal/corrections` | Admin cursor 队列、资质/产能写、异常队列 | M202 |
| Network Portal 整改详情 | 网点协作只读整改详情 UI | `IMPLEMENTED` | 复用 M202 GET correction-cases/{id}（`CorrectionCase`）；Admin Web `/network-portal/corrections/:id`（source snapshot + resubmissions）；列表深链；catalog 仍 v15；OpenAPI 仍 0.99.0；Flyway 仍 100/102 | Portal close/waive/ACK、新 pageId | M209 |
| Network Portal 运营异常详情 | 网点协作只读异常详情 UI | `IMPLEMENTED` | 复用 M203 GET operational-exceptions/{id}（`NetworkPortalExceptionItem`）；Admin Web `/network-portal/exceptions/:id`；列表深链；allowedActions=[]；catalog 仍 v15；OpenAPI 仍 0.99.0；Flyway 仍 100/102 | Portal ACK/resolve、新 pageId | M210 |
| Network Portal 资质详情 | 网点协作只读资质详情 UI | `IMPLEMENTED` | 复用 M205 GET technician-qualifications/{id}；Admin Web `/network-portal/qualifications/:id`（decided*/version）；列表深链；catalog 仍 v15；OpenAPI 仍 0.99.0；Flyway 仍 100/102 | Portal decide、FileObject、新 pageId | M211 |
| Network Portal 师傅关系详情 | 网点协作只读关系详情 UI | `IMPLEMENTED` | 复用 M206 GET technician-memberships/{id}；Admin Web `/network-portal/technicians/memberships/:id`（version）；列表深链；catalog 仍 v15；OpenAPI 仍 0.99.0；Flyway 仍 100/102 | 操作员 NetworkMembership、Portal decide、新 pageId | M212 |
| Network Portal 限定工单工作区 | 网点协作限定工作区薄快照 | `IMPLEMENTED` | `GET /network-portal/work-orders/{id}/workspace`；ACTIVE NETWORK 门禁；薄 DTO（头+任务）；**M424** 顶层脱敏客户联系；Page Registry `NETWORK.WORKORDER.WORKSPACE`；独立 Network Web；OpenAPI **1.0.90** | Admin workspace 复用、完整 §6.1 区块、目录客户列、Portal ACK | M213、M424 |
| Network Portal 工作区协作深链 | 工作区→整改/异常/任务 query 水合 | `IMPLEMENTED` | UI-only：任务行深链 + corrections/exceptions/tasks 水合 `taskId`；工作区 fan-in OPEN 整改/异常摘要（缺能力省略）；OpenAPI 仍 1.0.0；catalog 仍 v16；Flyway 仍 100/102 | SLA/Visit/表单 DTO、PII、Portal ACK、notifications | M214 |
| Network Portal 工作区预约联系 | 工作区预约/联系尝试客户端 fan-in | `IMPLEMENTED` | UI-only：按 taskIds fan-in M197/M199 appointments/contact-attempts；缺 manageAppointment 省略；OpenAPI 仍 1.0.0；catalog 仍 v16；Flyway 仍 100/102 | SLA/Visit/表单 DTO、PII、写控件 | M215 |
| Network Portal 工作区当前师傅 | 工作区师傅 displayName fan-in | `IMPLEMENTED` | UI-only：fan-in M194 technicians 解析 displayName/membershipId + 预约 window 只读；缺 technician.readOwnNetwork 省略；OpenAPI 仍 1.0.0；catalog 仍 v16；Flyway 仍 100/102 | SLA/Visit/表单 DTO、Admin workspace 复用、addressRef/PII | M216 |
| Network Portal 目录页师傅 fan-in | 工单/任务目录 displayName + 工作台基数深链 | `IMPLEMENTED` | UI-only：目录师傅名/既有列、工作台 ACTIVE 基数深链、详情残余深链；OpenAPI 仍 1.0.0；catalog 仍 v16；Flyway 仍 100/102 | 列表预约 N+1、SLA/Visit/表单、PII、notifications | M217 |
| Technician Portal Feed 字段展示 | Feed/日程/同步摘要 Accepted 字段 UI | `IMPLEMENTED` | UI-only：展示 M195 既有非 PII 字段 + 门户内深链/taskId 水合/sinceCursor；OpenAPI 仍 1.0.0；catalog 仍 v16；Flyway 仍 100/102 | 离线工作包、TASK.DETAIL、MESSAGE、PII | M218 |
| Technician Portal ME 页壳 | TECHNICIAN.ME 消费 /me* | `IMPLEMENTED` | UI-only：`/technician-portal/me` + listMeCapabilities；修正 ME 别名；OpenAPI 仍 1.0.0；catalog 仍 v16；Flyway 仍 100/102 | PROFILE/TASK.DETAIL/MESSAGE、离线、PII | M219 |
| Technician Portal 当前责任任务详情 | ACTIVE 当前师傅任务在线非 PII 详情与协作历史 | `IMPLEMENTED` | 当前责任门禁；Task/预约/联系/Visit 安全摘要；M262 在线签到/中断；M263 冻结表单；M264 Evidence Begin/PUT/Finalize；M265 TASK_SUBMISSION Snapshot 与服务端双输入 Task 完成；M266 独立整改 Task 多轮补传/重新提交；OpenAPI 1.0.26；Flyway 100/102 | 联系/预约写；完整表单草稿；真实 FieldOperation operationRefs 签退；弱网/后台/离线；MESSAGE/PROFILE、PII | M243～M246、M262～M266 |
| Network Portal 队列字段展示 | 整改/异常/资质/师傅列表 Accepted 字段 | `IMPLEMENTED` | UI-only：四列表 + 任务目录既有列 + handlingTaskId 深链；OpenAPI 仍 1.0.0；catalog 仍 v16；Flyway 仍 100/102 | ACK/decide、Admin Review 深链、SLA/Visit/表单、notifications | M220 |
| Network Portal 工作区 SLA 摘要 | 限定工单工作区薄 SLA 计数 | `IMPLEMENTED` | 扩展 workspace 可选 `slaSummary`；NETWORK `sla.read` soft-gate；按 ACTIVE taskIds 计 open/breached；OpenAPI 1.0.1；catalog 仍 v16；Flyway 仍 100/102；Admin Web + IT/E2E | Visit/表单摘要（已由 M222 交付）、Admin workspace 复用、PII、SLA 详情 | M221 |
| Network Portal 工作区 Visit/表单 | 限定工单工作区 Visit/表单提交摘要 | `IMPLEMENTED` | 扩展 workspace 可选 `visits`/`formSubmissions`；NETWORK `visit.read`/`form.read` soft-gate；复用 Admin 摘要字段集；OpenAPI 1.0.2；catalog 仍 v16；Flyway 仍 100/102；Admin Web + IT/E2E | definition/values、Evidence 摘要、Admin workspace 复用、独立 NP 列表 API | M222 |
| Network Portal 工作区 Evidence | 限定工单工作区 Evidence 槽位/资料项摘要 | `IMPLEMENTED` | 扩展 workspace 可选 `evidenceSlots`/`evidenceItems`；NETWORK `evidence.read` soft-gate；复用 Admin 摘要字段集；OpenAPI 1.0.3；catalog 仍 v16；Flyway 仍 100/102；Admin Web + IT/E2E | Admin workspace 复用、独立 NP Evidence 列表、缩略图/下载、Revision 图、definition JSON | M223 |
| Network Portal 工作台 SLA 风险 | 网点工作台薄 SLA 风险计数 | `IMPLEMENTED` | 扩展 workbench 可选 `slaSummary`；NETWORK `sla.read` soft-gate；跨 ACTIVE taskIds 聚合 RUNNING/BREACHED；OpenAPI 1.0.4；catalog 仍 v16；Flyway 仍 100/102；Admin Web + IT/E2E | 即将超时时间窗、SLA 详情/deeplink、notifications、Portal ACK | M224 |
| Network Portal 工作区整改摘要 | 限定工单工作区整改摘要 | `IMPLEMENTED` | 扩展 workspace 可选 `corrections`；NETWORK `evidence.read` soft-gate；复用 Admin 摘要字段集；OpenAPI 1.0.5；catalog 仍 v16；Flyway 仍 100/102；Admin Web + IT/E2E | reviews[]、Portal ACK、Admin workspace 复用、notifications | M225 |
| Network Portal 工作区异常摘要 | 限定工单工作区运营异常摘要 | `IMPLEMENTED` | 扩展 workspace 可选 `exceptions`；NETWORK `operations.exception.read` soft-gate；复用 `NetworkPortalExceptionItem`；OpenAPI 1.0.6；catalog 仍 v16；Flyway 仍 100/102；Admin Web + IT/E2E | Portal ACK/resolve、Admin exception-item 发明、notifications | M226 |
| Network Portal 工作区预约联系摘要 | 限定工单工作区预约/联系服务端摘要 | `IMPLEMENTED` | 扩展 workspace 可选 `appointments`/`contactAttempts`；NETWORK `networkPortal.manageAppointment` soft-gate；复用 Admin 摘要字段集；OpenAPI 1.0.7；catalog 仍 v16；Flyway 仍 100/102；Admin Web + IT/E2E | 完整 Appointment DTO、写控件、PII、notifications | M227 |
| Network Portal 工作区师傅摘要 | 限定工单工作区当前师傅服务端摘要 | `IMPLEMENTED` | 扩展 workspace 可选 `technicians`；NETWORK `technician.readOwnNetwork` soft-gate；复用 `NetworkPortalTechnicianItem`；OpenAPI 1.0.8；catalog 仍 v16；Flyway 仍 100/102；Admin Web + IT/E2E | PII、写控件、Admin workspace 复用、notifications | M228 |
| Network Portal 工作区审核摘要 | 限定工单工作区审核案例服务端摘要 | `IMPLEMENTED` | 扩展 workspace 可选 `reviews`；NETWORK `evidence.read` soft-gate；复用 Admin Review 摘要字段集；OpenAPI 1.0.9；catalog 仍 v16；Flyway 仍 100/102；ReviewCase NETWORK read 对齐；Admin Web + IT/E2E | 独立 NP Review API/pageId、Portal ACK/decide、Admin Review 深链、note/approvalRef/decidedBy、notifications | M229 |
| Network Portal 目录师傅摘要 | 工单/任务目录页师傅服务端摘要 | `IMPLEMENTED` | 扩展 work-orders/tasks 页可选 `technicians`；NETWORK `technician.readOwnNetwork` soft-gate；复用 `NetworkPortalTechnicianItem`；OpenAPI 1.0.10；catalog 仍 v16；Flyway 仍 100/102；Admin Web + IT/E2E | PII、写控件字段发明、列表预约 N+1、notifications、Portal ACK | M230 |
| Network Portal 目录预约摘要 | 工单/任务目录页预约服务端摘要 | `IMPLEMENTED` | 扩展 work-orders/tasks 页可选 `appointments`；NETWORK `networkPortal.manageAppointment` soft-gate；复用 Admin/NP 预约摘要；OpenAPI 1.0.11；catalog 仍 v16；Flyway 仍 100/102；Admin Web + IT/E2E | 完整 Appointment DTO、写控件、PII、目录 contactAttempts、notifications、Portal ACK | M231 |
| Network Portal 目录联系摘要 | 工单/任务目录页联系尝试服务端摘要 | `IMPLEMENTED` | 扩展 work-orders/tasks 页可选 `contactAttempts`；NETWORK `networkPortal.manageAppointment` soft-gate；复用 Admin/NP 联系摘要；OpenAPI 1.0.12；catalog 仍 v16；Flyway 仍 100/102；Admin Web + IT/E2E | PII/party/note/recording/actor、写控件、notifications、Portal ACK | M232 |
| Network Portal 目录整改摘要 | 工单/任务目录页资料整改服务端摘要 | `IMPLEMENTED` | 扩展 work-orders/tasks 页可选 `corrections`；NETWORK `evidence.read` soft-gate；复用 Admin/NP 整改摘要；OpenAPI 1.0.13；catalog 仍 v16；Flyway 仍 100/102；Admin Web + IT/E2E | 目录 SLA 风险、目录 evidence、独立 NP Correction CRUD、notifications、Portal ACK | M233 |
| Network Portal 目录 SLA 风险 | 工单/任务目录页 SLA 风险服务端摘要 | `IMPLEMENTED` | 扩展 work-orders/tasks 页可选 `slaRiskSummaries`；NETWORK `sla.read` soft-gate；keyed 薄计数；OpenAPI 1.0.14；catalog 仍 v16；Flyway 仍 100/102；Admin Web + IT/E2E | 即将超时窗口、完整 SlaInstance、notifications、Portal ACK | M234 |
| Network Portal 目录资料摘要 | 工单/任务目录页资料 Evidence 服务端摘要 | `IMPLEMENTED` | 扩展 work-orders/tasks 页可选 `evidenceSlots`/`evidenceItems`；NETWORK `evidence.read` soft-gate；复用 Admin/NP 工作区摘要；OpenAPI 1.0.15；catalog 仍 v16；Flyway 仍 100/102；Admin Web + IT/E2E | 缩略图/下载、Revision 图、definition JSON、独立 NP Evidence API、notifications、Portal ACK、用户脱敏 | M235 |
| Network Portal 目录工单头 | 工单/任务目录页服务产品/区域/接收时间 | `IMPLEMENTED` | 扩展 items 非 PII 头字段；`WorkOrderDirectoryHeaderQuery`；OpenAPI 1.0.16；catalog 仍 v16；Flyway 仍 100/102；Admin Web + IT/E2E | 用户脱敏 PII、独立 updatedAt、目录 reviews、notifications、Portal ACK | M236 |
| Network Portal 工作台统计时间 | 工作台 asOf/capacity.updatedAt 展示 | `IMPLEMENTED` | UI-only：页级「统计时间」=`asOf` + 容量行 `updatedAt`；OpenAPI 仍 1.0.16；catalog 仍 v16；Flyway 仍 100/102；Admin Web E2E | 今日/明日预约计数、签约比例/评分、PII、notifications、Portal ACK、产能申请 | M237 |
| Network Portal 预约联系历史字段 | 任务页预约/联系历史操作者与渠道 | `IMPLEMENTED` | UI-only：Appointment.createdBy + revision channel/party/window；ContactAttempt.actorId/channel；禁止 addressRef/note；OpenAPI 仍 1.0.16；catalog 仍 v16；Flyway 仍 100/102；Admin Web E2E | 工作区/目录摘要扩 actor、今日/明日预约计数、notifications、Portal ACK、PII | M238 |
| Network Portal 工作区现场摘要字段 | 工作区 Visit/表单/Evidence Accepted 字段展示 | `IMPLEMENTED` | UI-only：补齐 M222/M223 非 PII 摘要字段；OpenAPI 仍 1.0.16；catalog 仍 v16；Flyway 仍 100/102；Admin Web E2E | GPS/note/values/definition/file、Admin workspace 复用、notifications、Portal ACK、PII | M239 |
| Network Portal 工作区协作摘要字段 | 工作区预约/联系/整改/审核/异常/师傅 Accepted 字段展示 | `IMPLEMENTED` | UI-only：补齐协作摘要非 PII 字段 + correction/handling 深链；任务页联系时间；OpenAPI 仍 1.0.16；catalog 仍 v16；Flyway 仍 100/102；Admin Web E2E | 摘要扩 actor、PII、Portal ACK、notifications、Admin workspace 复用 | M240 |
| Network Portal 预约联系历史残余字段 | 任务页预约/联系历史范围与动作字段 | `IMPLEMENTED` | UI-only：Appointment project/workOrder/technician/network/createdAt/allowedActions；ContactAttempt project/workOrder/createdAt；OpenAPI 仍 1.0.16；catalog 仍 v16；Flyway 仍 100/102；Admin Web E2E | addressRef/note/party/recording、PII、Portal ACK、notifications、今日/明日预约计数 | M241 |
| Network Portal 整改详情残余字段 | 整改详情 closed/waived 操作者与补传 submittedBy | `IMPLEMENTED` | UI-only：closedBy/waivedBy/waiveApprovalRef/waiveNote + resubmissions.submittedBy；OpenAPI 仍 1.0.16；catalog 仍 v16；Flyway 仍 100/102；Admin Web E2E | Portal close/waive 写控件、摘要扩 waiveNote、PII、notifications | M242 |
| Network Portal 运营异常队列 | 网点协作只读异常发现面 | `IMPLEMENTED` | list/get operational-exceptions；复用 `operations.exception.read` NETWORK；Page Registry `NETWORK.EXCEPTION.QUEUE`；Admin Web `/network-portal/exceptions`；allowedActions 恒为空 | Portal ACK/resolve、Admin cursor 队列、产能写 | M203 |
| Network Portal 师傅关系与资质 | 网点协作写命令（membership/qualification） | `IMPLEMENTED` | create/terminate membership + submit qualification；`networkPortal.manageTechnician`；NETWORK 收窄 `network.manageTechnician`；Page Registry `NETWORK.QUALIFICATION` + 扩展 `NETWORK.TECHNICIAN.LIST`；Admin Web 控件 | Portal decide、FileObject、createTechnicianProfile、产能申请 | M204 |
| Network Portal 资质只读列表 | 网点协作只读资质发现面 | `IMPLEMENTED` | list/get technician-qualifications；复用 `technician.readOwnNetwork` NETWORK；ACTIVE 师傅 fan-in；Page Registry `page-registry-v12` 扩展 `NETWORK.QUALIFICATION`；Admin Web `/network-portal/qualifications` | Portal decide、FileObject、产能申请 | M205 |
| Network Portal 师傅关系只读列表 | 网点协作只读关系发现面 | `IMPLEMENTED` | list/get technician-memberships；复用 `technician.readOwnNetwork` NETWORK；默认 ACTIVE；含真实 version；Page Registry `page-registry-v13`；Admin Web 终止表单填充真实 version | 操作员 NetworkMembership、Portal decide、产能申请 | M206 |
| Network Portal 工作台能力门控计数 | 网点协作工作台 enrichment | `IMPLEMENTED` | 扩展 workbench：`unassignedTechnicianTaskCount` + 能力门控 correction/exception/qualification 计数；缺能力省略；Page Registry `page-registry-v14`；Admin Web capacity/深链；OpenAPI 0.99.0；Flyway 仍 100/102 | SLA 风险计数、产能申请、Portal ACK/decide | M207 |
| Network Portal 产能页 | 网点协作只读产能发现面 | `IMPLEMENTED` | 复用 M194 `GET /network-portal/capacity`；Page Registry `NETWORK.CAPACITY` + `page-registry-v15`；Admin Web `/network-portal/capacity`（含 `version`）；工作台深链；OpenAPI 仍 0.99.0；Flyway 仍 100/102 | 产能申请/写、`CapacityAdjustmentRequest`、未 Accepted 字段 | M208 |
| Network Portal 预约协作 | 网点协作写命令（预约） | `IMPLEMENTED` | propose/confirm/list；`networkPortal.manageAppointment`；拒绝 TECHNICIAN 确认伪装；委托 AppointmentService；Admin Web 表单 | 资料补传（爽约/联系见 M199） | M197 |
| Network Portal 预约生命周期 | 网点协作写命令（改约/取消） | `IMPLEMENTED` | reschedule/cancel；复用 `networkPortal.manageAppointment`；If-Match；委托 AppointmentService；Admin Web 动作 | 资料补传（爽约/联系见 M199） | M198 |
| Network Portal 爽约与联系 | 网点协作写命令（爽约/联系） | `IMPLEMENTED` | mark-no-show + contact-attempts list/record；复用 `networkPortal.manageAppointment`；委托 AppointmentService；Admin Web 动作 | 资料补传、Visit（改派见 M200） | M199 |
| Technician Portal Feed | 师傅任务 Feed / 日程 / 同步摘要 / 我的 / 当前任务详情 / 在线整改 | `IMPLEMENTED` | API-06 §11 子集；`X-Technician-Context`；本人 ACTIVE TECHNICIAN assignment Feed；tombstone；schedule；sync-summary；独立 H5/iOS；M218/M219/M243～M246 只读详情；M262 在线 Visit；M263 表单；M264 Evidence 上传；M265 Snapshot/Task 完成；M266 整改 Task；**M359** Feed 注解/详情头能力预检拒单 | 离线工作包、mobile sync commands、MESSAGE、PROFILE、联系/预约、完整表单、真实签退 | M195、M218、M219、M243～M246、M257～M266、M359 |
| Consumer Identity | CustomerProfile、用户资源关系和 C 端身份 | `ACCEPTED` | Principal/IdentityLink/API Schema 已预留 Consumer Persona | 身份治理序列之后的独立 Epic；待登录、隐私、客户主数据与注销策略确认；不得宣称已实现 | 后续正式 Epic |
| 项目治理 | Project 核心事实、范围关系与授权目录 | `PARTIAL` | 项目创建；REGION/NETWORK 当前关系整组修订和不可变历史；`project.read` 授权目录、详情及历史查询 | owners、品牌/服务产品/配置绑定、生命周期、计划修订审批、目录治理 UI | M8、M64～M67 |
| 可靠消息 | Inbox、Outbox、Worker claim/lease/retry | `IMPLEMENTED` | 本地可靠发布消费、恢复和人工接管基础 | 正式 Broker 和跨服务运行 | M9～M10 |
| 配置中心 | 不可变配置资产、Bundle 发布、设计器与灰度通道 | `PARTIAL` | M282～M296 设计器/治理 + **M303～M309** 六类运行时 + **M310～M315** 设计器 + **M321～M350** 配置驱动履约主链路；**M356～M363** 客户端能力门禁；**M366** Bundle 定向目标派单求交；**M378～M383** Profile/Revision/Manifest/Resolver/发布/冻结；**M385** 配置中心母版 + Runbook/Compare-Impact；**M388** 结构化 Draft Document（OpenAPI 1.0.63） | Workflow/Task 模板中心（M386/M387）；完整校验 30 条；AMOUNT/加权、editableWhen、iOS 条件全量硬阻断；BUSINESS 暂停/预警 | M16、M33、M36、M52～M53、M61、M268、M271、M281～M296、M303～M315、M321～M350、M356～M363、M366、M378～M388 |
| 外部接入 | BYD CPIM + REFERENCE_OEM SAMPLE + Geely 本地切片 | `PARTIAL` | BYD 入站建单/更新/取消、提审、回调；**M267/M297～M302** 通用 SPI；**M311/M314/M316/M320** 吉利本地 + 三 OEM 并行冒烟 + **M317～M319** 远端查询/人工处置/批量重放；**M321～M347** 冻结 Mapping（含 REFERENCE_OEM SAMPLE CREATE/UPDATE/CANCEL + Admin Mapping DSL UI） | 吉利 Sandbox/OpenAPI 签名联调（BLOCKED_EXTERNAL）、生产凭据/对象存储 | M16、M56～M60、M77～M79、M99、M158、M267、M272、M311、M314、M316、M317、M318、M319、M320、M321～M347、M273、M297～M302 |
| 工单 | WorkOrder 接收、激活、履约完成与授权工作区投影 | `PARTIAL` | 权威工单、工作流启动、跨阶段和 END 完结；授权目录、非 PII 详情、Stage/Task 执行骨架及核心执行+现场履约时间线 | 完整取消、暂停、恢复、客户敏感详情审计、跨域完整时间线/动作与全部业务分支 | M16～M19、M68～M69、M73～M74 |
| 工作流 | 线性 + 网关 + WAIT/TIMER + SUB_PROCESS + 多实例 + 取消/重开/跳转/补偿 + 标准模板 | `PARTIAL` | 上项 + **M281** 维修/移机/巡检标准模板（含家充勘安） | HTTP 命令面、表单/资料完整模板包、设计器 | M17～M19、M61、M69、M268～M271、M275～M281 |
| 人工任务与执行历史 | claim/start/complete、责任、执行保护与授权任务读取 | `IMPLEMENTED` | 人工命令、候选领取、唯一责任、release/reclaim、执行保护；表单/资料完成门禁；授权队列/详情、allowed-actions、自动 Attempt 历史及工单内核心 Task 生命周期与指派/Guard/人工接管时间线 | block/retry/cancel 等其他动作、Workflow Node 历史、跨工单/跨域完整历史和 Review 完成条件 | M20～M23、M35、M41、M43、M69～M73、M81 |
| 应用只读投影 | 工作区、队列、时间线和投影运行时 | `PARTIAL` | 独立 readmodel 模块；核心执行、现场履约、SLA、资料/审核/整改（含外部回执与条件 KEEP/INVALIDATE 处置）、外发交付全链路、异常确认/闭环、ServiceAssignment 与 Task 指派/Guard/人工接管 Inbox 投影；授权时间线与稳定分页及最近活动摘要；时间线 checkpoint/dead letter/generation 重建与 FRESH/LAGGING/UNKNOWN/REBUILDING freshness；definition 登记、dead letter 幂等重放与旧/孤儿 generation 清理；工单工作区顶层实时组合、当前 ACTIVE 服务责任摘要与 TASKS/TIMELINE_AUDIT/APPOINTMENTS_VISITS（含联系尝试）/FORMS_EVIDENCE（含提交与资料项安全元数据）/REVIEWS_CORRECTIONS（含 CLIENT/重开血缘）/INTEGRATION 按需区块（敏感字段最小化；缺权次级区块降级）；**M351 FINAL_REVIEW 终审 Fan-in（脱敏摘要 + Snapshot targets + 门禁）**；授权跨项目 ReviewCase/CorrectionCase/OutboundDelivery/InboundEnvelope 专项队列；Admin 个人 SavedView（M189）、UI Preference（M190）、共享 SavedView（M191）、受控全局搜索 fan-in（M192）与最近访问（M193）；Network Portal 只读 fan-in（M194）；Technician Portal Feed fan-in（M195） | 试算合并、revision/slots 技术噪声、表单值与资料版本详情、FACTS_CALCULATIONS、完整事件 taxonomy/过滤、通用 work-queues、共享偏好、`search_document` 索引平台、多投影平台、Broker offset、离线工作包、Admin 重建/重放 HTTP；终审 UI/decide（M352～M353） | M73～M99、M158、M189～M195、M351 |
| 服务分配 | 网点分配、容量、改派 Saga、超时恢复 | `IMPLEMENTED` | ServiceAssignment、容量权威、改派、终止、对账和自动恢复；**M324/M332/M337/M338/M348** 冻结 DISPATCH → ACTIVE NETWORK（Coverage 地图 + ORDER_COUNT 比例缺口）+ TECHNICIAN + Admin scope/fallback/allocationRatio 编辑器；**M366** 自动 TECHNICIAN 池 `supportedClientKinds` 硬过滤 + MANUAL/`CLIENT_KIND_TARGET_EMPTY`；**M367** Manual/Network assign/reassign kind 硬拒绝（422 + DENY） | 完整策略评分、金额/加权比例、师傅 Coverage、全部异常分支和 Coverage CRUD UI | M24～M28、M324、M332、M337、M338、M348、M366、M367 |
| 运营异常 | 异常工作台基础 | `PARTIAL` | 异常记录和恢复入口；M58 将外发 UNKNOWN 与 Task 最终人工事件汇入 OperationalException + HUMAN Task；M59 提供高风险人工重发事实；M60 在严格 ACK 后幂等闭环对应异常并处理事件乱序；列表/详情/确认已硬化为实时项目范围 | 其他异常类型自动闭环、完整通知、运营中心前端和跨域异常目录 | M29、M58～M60、M100 |
| 预约 | 预约修订、联系终态动作 | `PARTIAL` | Revision、并发和终态动作基础；公开事件已并入工单时间线；Admin propose/confirm E2E；`GET /contact-attempts/{id}` 与详情页；Network Portal propose/confirm（M197）；reschedule/cancel（M198）；mark-no-show/contact（M199） | 用户确认渠道、完整日程、资料 Network 写 | M30～M31、M74、M136、M160、M197～M199 |
| 现场作业 | Visit 生命周期 | `PARTIAL` | Visit 运行时基础；签到/签退/中断事件已并入工单时间线；Admin check-in/check-out E2E；`GET /visits/{id}` 与详情页；M262 Technician Context/当前责任双校验、H5/iOS 一次性定位签到与中断 | GPS 策略增强、真实 operationRefs 签退、完整现场提交、离线同步、真机定位 | M32、M74、M136、M159、M262 |
| 动态表单 | 资产、冻结版本、不可变提交和 Task 完成门禁 | `PARTIAL` | 固定/条件 required、visible 与布尔 validation rule，基础类型校验、精确版本提交和完成引用；form.submitted 已并入工单时间线；M263 H5/iOS 冻结基础字段在线提交 | 可移植客户端条件/选项/高级控件、复杂 validator、计算字段、草稿、冲突、更正和审核 | M33～M35、M53、M76、M263 |
| 资料 Evidence | 资产、槽位、Item/Revision、机器校验、Snapshot、完成门禁、作废、Review、Correction | `PARTIAL` | 固定/条件槽位、表单触发只追加重解析、槽位世代/lineage、REVIEW_REQUIRED 与 KEEP/INVALIDATE、安全文件联动、Snapshot/完成门禁及审核整改；M264 H5/iOS 在线前台 Begin/PUT/Finalize；M265 在线 Snapshot 与 Task 完成；M266 在线整改上传/Snapshot/resubmit | OCR/CV、GPS 权威距离、长期归档、生产扫描/对象存储、弱网/后台/离线上传、自动整改目标 | M36～M53、M76、M82～M83、M264～M266 |
| 安全文件 | Begin/Finalize/隔离/扫描/授权下载/作废 | `IMPLEMENTED` | 独立安全文件生命周期；Evidence 编排 Begin/Finalize/Invalidate 联动 | 正式对象存储、专业扫描服务、物理删除 | M11、M38、M46 |
| 审核整改 | ReviewCase、ReviewDecision、CorrectionCase | `PARTIAL` | Review + Correction + 整改 Task + 强制通过/重开 + 车企回执 + WAIVED；CLIENT Case 来源、批次/mapping 冻结；交付明确成功后自动创建 CLIENT Case/Route，UNKNOWN 可授权人工重发并在严格 ACK 后闭环异常；授权跨项目 ReviewCase 与 CorrectionCase 队列；M266 H5/iOS 领取、开始、多轮补传、Snapshot 与 resubmit，reviewer close 权威完成整改 Task；**M351～M355** 终审 Fan-in/UI/decide/整改深链/E2E；**M360** 终审 8 态视觉基线；**M364** INTERNAL `reviewTaskId` handling Task；**M365** 试点 `REVIEW_TASK` WAITING 门闸 + `evidence.review-decided` 唤醒（A5-B） | CLIENT `reviewTaskId`、全量模板、SLA/assignee enrich、多候选人/转派、审核人移动端、自动 Evidence target 映射 | M44～M60、M97～M98、M266、M351～M355、M360、M364、M365 |
| SLA | 时钟、预警、升级 | `PARTIAL` | Task `TASK_CREATED→TASK_COMPLETED` ELAPSED 时钟；**M369** Bundle 锁定 `CALENDAR` 的 BUSINESS 截止纯函数与业务已用时长（ADR-090 D1-R）；显式策略/日历版本摘要锁定；TARGET_DUE 对账；RUNNING/BREACHED/MET/MET_LATE；Inbox/Outbox 与不可变 segment/milestone；`sla.read` 跨项目工作台/时间线/详情；`sla.started@v2` | 暂停/恢复、免责/重算、预警/升级/通知、其他 subject、组织关系、Portal 前端、考核结算、外部节假日 API | M61～M66、M75、M369 |
| 通知 | 通知策略运行时与投递 | `PARTIAL` | **M307** 冻结 Bundle `NotificationRuntime`；**M326** `task.created`/`task.completed` 自动订阅 → Inbox + RoleGrant 收件人 → resolveAndDispatch → Intent/Delivery/Attempt 持久化；LocalReference SENT 本地 ACK，UNKNOWN/FAILED 人工接管 | 模板渲染、真实短信/邮件/Push 供应商、Admin 投递工作台、网络 I/O 移出事务与业务重试 Task 时钟 | M307、M326、`architecture/14-*` |
| 履约事实与试算 | 事实提取和双向试算 | `PARTIAL` | **M309** `PricingRuntime`；**M327** `workorder.fulfilled` → 最小履约事实 + SHADOW `CalculationSnapshot`（不落账） | 完整 FactDefinition/CalculationRun、应收/应付双轨、对账结算、Admin 计价工作台、AUTHORITATIVE | M309、M327、M5 设计 |
| 对账结算 | 对账、结算、争议与调整 | `PROPOSED` | 已有边界设计 | 正式运行时和页面 | `architecture/16-*` |
| Admin Portal | 总部运营后台 | `PARTIAL` | **M284/M287/M289/M291/M292/M294/M295/M296** 配置设计器；M101～M193 运营基线；**M328** UNKNOWN；**M351** 终审；**M370～M377** 设计系统；**M384～M389** 配置四核与工单详情；**M397～M406** 用户管理、项目选择器主数据、关注项目；**M409** 关注项目待办/SLA 角标；**M414** 主数据治理台；**M415～M420** 主体变更时间线与授权拒绝安全活动流；**M422** 履约使用中工单摘要；**M423** 工单工作区脱敏客户联系（`READY_FOR_REVIEW`） | 全国区县全量树/拼音索引/多级子品牌、失败登录/设备指纹、正式企业 OIDC/BFF；不得宣称 PRODUCT_ACCEPTED | M7 设计、M101～M193、M284～M296、M328、M351、M370～M377、M384～M389、M397～M406、M409、M414～M420、M422～M423、Admin 试点基线 |
| Network Portal | 网点协作端 | `PARTIAL` | M194～M242 读写切片；M256 独立 `serviceos-network-web`；**M390～M392** 工作台/工作区/整改异常；**M396** 师傅与产能；**M407～M413** 分配候选/距离亲和/推荐解释/今日时间轴/预约日历；**M421** 师傅列表开放任务/资质摘要；**M424** 工作区脱敏客户联系（`READY_FOR_REVIEW`） | 技能 taxonomy/服务区域/最近同步/资质到期、经纬度/路网距离、数值推荐评分、月视图拖拽改约、目录客户字段、产能申请写、Portal ACK/resolve/decide、notifications、生产 IdP | M7 设计、M194～M242、M255～M256、M390～M392、M396、M407～M413、M421、M424 |
| Technician App / Portal | 师傅移动端与 Feed | `PARTIAL` | M195/M218/M219/M243～M246 只读安全切片；M257 独立 H5；M258～M261 iOS 基础；M262 在线 Visit；M263 冻结基础表单；**M349/M350** H5 条件执行器 + 工单/区域权威头 + validationRules；M264 Evidence 采集上传；M265 Snapshot 与 Task 完成；M266 在线整改；**M357～M363** 运行时能力拒单、定向目标外拒单、Feed/详情头预检、整改路径门禁、列表预检与领取/启动硬拒 | 联系/预约、完整表单草稿、editableWhen/默认值（未接受）、iOS 共用执行器、真实 operationRefs 签退；弱网/后台/Track F 离线；签名真机/真实 IdP/VoiceOver/崩溃采集/TestFlight | M7 设计、M195、M218～M219、M243～M246、M257～M266、M349、M350、M357～M363 |
| External Portal | 用户/车企受控页面 | `PROPOSED` | 最小边界规划 | 二期页面和工程实现 | M7 设计 |

## 4. 里程碑历史摘要

各里程碑的「已实现 / 明确未实现」摘要已迁移至 [implementation-status-archive.md](implementation-status-archive.md)（M33～M182）；一行式里程碑索引见 [milestone-index.md](milestone-index.md)；权威范围以对应 `architecture/Mxx` 实现文档和 `testing/Mxx` 验收矩阵为准。

该 archive 冻结保存拆分前的历史叙事，不再追加新里程碑摘要。新里程碑以实现文档、验收矩阵、追踪矩阵和本文件当前状态为权威，并重新生成索引。

## 5. 下一实施方向

当前序列：

- **M384 Implemented（文档）**：Admin 产品蓝图与经典专业风事实源；纠正 M383 产品状态。
- **M385 Implemented（切片 A）**：共享经典专业风基础 + 项目履约配置中心母版 + 新建向导 + Runbook/Compare-Impact API + Playwright/截图；产品状态 **`READY_FOR_REVIEW`**。
- **M388 Implemented**：结构化 `ProjectFulfillmentDocument` Draft 读写；编辑器去产品路径 `documentJson`（OpenAPI **1.0.63**）；产品状态仍 **`READY_FOR_REVIEW`**。
- **M386 Implemented**：Admin 工作流设计器产品页 `/configuration/workflows`；真实 WORKFLOW Draft API；无 JSON 主编辑；`READY_FOR_REVIEW`。
- **M387 Implemented**：任务模板中心读模型 + 产品页（OpenAPI **1.0.64**）；`READY_FOR_REVIEW`。
- **M389 Implemented**：工单详情统一履约工作区（进度条/允许动作栏/当前任务卡/右轨）；`READY_FOR_REVIEW`。
- **M390 Implemented**：Network 工作台产品化 + 分配师傅抽屉（经典专业协作风）；`READY_FOR_REVIEW`。
- **M391 Implemented**：Network 工单工作区产品外壳 + 预约协同面板；`READY_FOR_REVIEW`。
- **M392 Implemented**：Network 资料整改与异常中心产品化；`READY_FOR_REVIEW`。
- **M393 Implemented**：Technician H5 今日任务产品化（移动壳/卡片/概览）；`READY_FOR_REVIEW`。
- **M394 Implemented**：Technician H5 任务详情作业闭环产品化（步骤条/签到前置/提交前检查/底部主操作）；`READY_FOR_REVIEW`。
- **M395 Implemented**：Technician H5 整改与同步冲突中心产品化；`READY_FOR_REVIEW`。
- **M396 Implemented**：Network 师傅与产能产品化；`READY_FOR_REVIEW`。本轮曾评估 Technician iOS，但当前环境无 Xcode，改为本切片。
- **M397 Implemented**：Admin 用户管理母版产品化（目录 ListPageLayout + 详情 Tabs）；`READY_FOR_REVIEW`。
- **M398 Implemented**：Admin 项目管理列表+新建专用流程、工作台风险 SummaryStrip；`READY_FOR_REVIEW`。
- **M399 Implemented**：项目列表履约方案聚合计数（OpenAPI **1.0.65** + soft-gate）；关闭对应 UI_DATA_GAP。
- **M400 Implemented**：项目创建/范围编辑车企·区域·网点实体选择器（OpenAPI **1.0.66** `ProjectReferenceOptions` + ServiceNetwork 目录）。
- **M401 Implemented**：Admin 关注项目读模型（Flyway **V139** + OpenAPI **1.0.67** `/me/followed-projects` + 工作台/项目详情）。
- **M402 Implemented**：Admin 用户登记（无密码）与目录组织/角色摘要（Flyway **V140** + OpenAPI **1.0.68**）。
- **M403 Implemented**：Admin 主体最近登录读模型（Flyway **V141** + OpenAPI **1.0.69** `recent-logins`）。
- **M404 Implemented**：Admin 用户详情组织任职编辑（OpenAPI **1.0.70** `GET /org-memberships` 摘要 + 创建/调动/终止）。
- **M405 Implemented**：Admin 主体变更时间线（OpenAPI **1.0.71** `change-timeline`：生命周期 + 审计 + 登录）。
- **M406 Implemented**：车企主数据与行政区名称目录（Flyway **V142** + OpenAPI **1.0.72** `/project-clients` `/region-catalog`）。
- **M407 Implemented**：Network 分配师傅候选摘要（OpenAPI **1.0.73** `assign-candidates`：开放任务/资质/产能警告）。
- **M408 Implemented**：分配候选预约日程冲突摘要（OpenAPI **1.0.74**：未完成预约数/窗口重叠）。
- **M409 Implemented**：Admin 关注项目待办/SLA/工单角标聚合（OpenAPI **1.0.75**：soft-gate 计数 + 截断标记）。
- **M410 Implemented**：Network 分配候选行政区距离亲和（OpenAPI **1.0.76**：`distanceTier`/`distanceSummary`/`coverageMatched` + 工单区域摘要；不伪造经纬度）。
- **M411 Implemented**：Network 工作台今日预约时间轴（OpenAPI **1.0.77**：`todayTimeline`/`todayAppointments`；Asia/Shanghai 运营日；manageAppointment soft-gate）。
- **M412 Implemented**：Network 分配候选推荐解释（OpenAPI **1.0.78**：`recommendationTier`/`recommendationSummary`/`rankingExplanation`；无内部评分公式）。
- **M413 Implemented**：Network 预约日历视图（OpenAPI **1.0.79**：`/appointment-calendar`；Page Registry v21；默认 14 天运营日）。
- **M414 Implemented**：Admin 主数据治理台（OpenAPI **1.0.80** + Flyway **V143** + Page Registry v22：车企启停、品牌目录、省级骨架/懒加载树）。
- **M415 Implemented**：Admin 跨聚合主体变更时间线（OpenAPI **1.0.81**：MEMBERSHIP/ROLE_GRANT + actorDisplayName + omittedSources soft-gate）。
- **M416 Implemented**：Admin 网点任职并入主体变更时间线（OpenAPI **1.0.82**：`NETWORK_MEMBERSHIP` + `network.read` soft-gate）。
- **M417 Implemented**：Admin 师傅服务关系并入主体变更时间线（OpenAPI **1.0.83**：`TECHNICIAN_MEMBERSHIP` + `network.read` soft-gate）。
- **M418 Implemented**：Admin 师傅档案生命周期并入主体变更时间线（OpenAPI **1.0.84**：`TECHNICIAN_PROFILE` + `network.read` soft-gate）。
- **M419 Implemented**：Admin 主体授权拒绝安全活动流（OpenAPI **1.0.85** + Flyway **V144**：独立 `authorization-denials`，不并入 change-timeline）。
- **M420 Implemented**：Admin 师傅客户端种类声明并入主体变更时间线（OpenAPI **1.0.86** + Flyway **V145**：修复 CHECK + `TECHNICIAN_CLIENT_KINDS_DECLARED` 投影）。
- **M421 Implemented**：Network 师傅列表资质与开放任务摘要（OpenAPI **1.0.87**：`NetworkPortalTechnicianItem` 开放任务/资质计数，口径对齐 M407）。
- **M422 Implemented**：Admin 履约配置中心使用中工单摘要（OpenAPI **1.0.88**：`fulfillment-usage-summary`，ACTIVE 计数 + `workOrder.read` soft-gate）。
- **M423 Implemented**：Admin 工单工作区脱敏客户联系摘要（OpenAPI **1.0.89**：workspace `masked*`，复用 M351 脱敏端口）。
- **M424 Implemented**：Network 工单工作区脱敏客户联系摘要（OpenAPI **1.0.90**：NP workspace `masked*`，网点范围 `getMaskedContactForNetwork`）。
- **M383** 长链路 test 7/8 仍未闭合，不阻塞本切片。

建议下一批（按优先级）：

1. Technician iOS 正式离线闭环（需 macOS/Xcode 环境）；
2. 全国区县全量树 / 拼音索引 / 多级子品牌（需权威 GB 数据集与导入脚本；M414 已交治理主路径）；
3. 失败登录/设备指纹（当前仅成功登录事实；需 IdP 失败事件）/ 表单资料缩略图与完整审核记录。

仍为 **硬门禁 / 外部阻塞**（不可发明推进）：

1. AMOUNT/加权比例 — **需业务确认口径**后方可实施；
2. BUSINESS 暂停/预警/升级或结算落账 — R3 大切片，需独立批准；
3. 吉利真实联调材料到位后提升为最高优先级；否则保持 `BLOCKED_EXTERNAL`。

仍为 **`BLOCKED_EXTERNAL`**：

- 吉利 Sandbox URL / 联调 AK·SK·IV / OpenAPI 平台统一签名与真实脱敏报文；
- Swift/Xcode、签名真机、TestFlight；
- Track F/G 外部证据。


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
