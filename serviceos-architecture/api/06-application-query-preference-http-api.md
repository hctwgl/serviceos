---
title: 应用工作区、队列与用户偏好 HTTP API
version: 0.1.0
status: Accepted
---

# 应用工作区、队列与用户偏好 HTTP API

## 0. 接受范围（M85 / M87 / M88 / M89 / M90 / M91 / M92 / M93 / M94 / M95 / M96 / M97 / M98 / M99 / M100 / M158 / M189 / M190 / M191 / M192 / M193 / M194 / M195 / M202 / M203 / M205 / M206 / M207 / M213 / M214 / M215 / M216 / M217 / M218 / M219 / M220 / M221 / M222 / M223 / M224）

**Accepted（可指导实现）**：

- §2 通用查询元数据；
- §5 中 `GET /api/v1/work-orders/{id}/workspace` 顶层组合快照（M85）；
- §5 顶层工作区 `serviceAssignmentSummary` 当前 ACTIVE 服务责任摘要（M92）；
- §5 `GET /api/v1/work-orders/{id}/activity-summary` 最近时间线条目摘要（M93）；
- §5 中 `GET /api/v1/work-orders/{id}/workspace/sections/{section}`：
  `TASKS`、`TIMELINE_AUDIT`（M87）、`APPOINTMENTS_VISITS`（M88）、
  `FORMS_EVIDENCE`（M89）、`REVIEWS_CORRECTIONS`（M90）与 `INTEGRATION`（M91）。
- `APPOINTMENTS_VISITS.contactAttempts` 安全联系尝试摘要（M94）。
- `FORMS_EVIDENCE.formSubmissions/evidenceItems` 安全运行时元数据（M95）。
- `REVIEWS_CORRECTIONS.reviews` CLIENT/重开血缘元数据（M96）。
- §6 `GET /api/v1/review-cases` 授权审核案例队列（M97）；不接受通用 work-queues。
- §6 `GET /api/v1/correction-cases` 授权整改案例队列（M98）；不接受通用 work-queues。
- §6 `GET /api/v1/outbound-deliveries` 授权外发交付队列（M99）；不接受通用 work-queues。
- §6 `GET /api/v1/operational-exceptions` 运营异常项目范围硬化（M100）；不接受通用 work-queues。
- §6 `GET /api/v1/inbound-envelopes` 授权入站 Envelope 队列（M158）；仅含已绑定
  `projectId` 的安全摘要；不接受 null-project 可见性、原文下载或通用 work-queues。
- §8 个人 SavedView CRUD（M189）：`GET/POST /me/saved-views`、
  `PUT/DELETE /me/saved-views/{id}`；Portal=`ADMIN`；pageId 限于已有 Accepted 筛选目录的
  Admin 页面（至少 `ADMIN.TASK.QUEUE`、`ADMIN.WORKORDER.LIST`、`ADMIN.CORRECTION.QUEUE`）。
- §8 共享 SavedView（M191）：`POST /api/v1/saved-views/{id}:share` 仅针对 Portal=`ADMIN`、
  由调用方拥有的个人视图。可见性接受 `ROLE`（`sharedScopeRef=roleId`）与租户级 `TENANT`
  （`sharedScopeRef` 为空）；本切片不接受组织树 `ORGANIZATION` 共享。共享只共享**查询定义**，
  **不**授予 capability 或数据访问权；应用共享视图时业务列表/队列仍重新鉴权。
  `GET /me/saved-views?pageId=` 须返回本人 `PRIVATE` 视图，以及当前主体可见的共享视图
  （同租户 `TENANT`，或 `ROLE` 且主体持有对应有效 RoleGrant）。取消共享：同一 share 端点
  body `visibility=PRIVATE`（owner 始终可收回；分享出 PRIVATE 需要
  `preference.shareSavedView` HIGH capability）。不接受独立 `:unshare` 路径本切片外的语义扩展。
  **不**接受 Network/Technician SavedView。
- §9 Admin UI Preference（M190）：仅
  `GET /me/ui-preferences?portal=ADMIN`、
  `PUT /me/ui-preferences`（body：`portal` + `preferences` 映射）、
  `DELETE /me/ui-preferences/{key}` 恢复默认；Portal 必须为 `ADMIN`，其他 Portal 失败关闭。
  允许键白名单见 §9；禁止关闭安全确认、隐藏必填、绕过脱敏或禁用事务通知。
  **不**接受共享偏好、Network/Technician Portal 偏好。
- §7 Admin 受控全局搜索（M192）：`GET /api/v1/search?q=&types=`；Portal 上下文仅
  `ADMIN`。本切片接受 type 子集：`WORK_ORDER`、`EXTERNAL_ORDER`、`NETWORK`、
  `TECHNICIAN`。**不**接受 `VEHICLE` / `CHARGER`（客户端请求未支持 type 时返回
  `SEARCH_TERM_NOT_ALLOWED` / HTTP 422）。手机号形态查询仅允许末四位精确；响应与日志
  **不**回显完整敏感 `q`（仅 `qDigest` / 掩码）。结果项：`resourceRef`、`type`、
  `primaryLabel`、`maskedSecondaryLabel`、`matchReason`、`deepLink`。服务端经既有授权
  查询端口应用当前 ScopePredicate；搜索**不**授予 capability。需要 `search.read`（HIGH）
  加 underlying type 读能力：缺 type 能力时省略该 type（降级），缺 `search.read` 则整请求
  403。实现为授权查询 fan-in / 受控精确·前缀回退，**不**要求 `search_document` 索引平台。
- §3 Admin 最近访问（M193）：仅
  `GET /api/v1/me/recent-resources` 与
  `PUT /api/v1/me/recent-resources`（upsert / touch，body：`resourceType`、`resourceId`、
  可选 `pageId`/`displayRef`；portal 固定 `ADMIN`）。
  本切片 resourceType：`WORK_ORDER`、`TASK`、`PROJECT`、`NETWORK`、`TECHNICIAN`。
  任意已认证主体仅可读写自己的最近访问列表，**不**要求新 capability；读取时对每项经既有
  授权查询端口重新鉴权，失权项**省略**（不整列表 403），并可在读路径删除陈旧行。
  列表上限（如 20）；同 `(principal, portal, resourceType, resourceId)` upsert
  `lastVisitedAt`。`displayRef` 仅为非敏感短标签，不得保存完整地址/电话/价格。
  **不**接受 `GET /me/notifications`、`GET /me/application-context`（后者与 M188 `/me`
  重叠；通知属独立 Epic）。**不**接受 Network/Technician Portal 最近访问。
- §10 Network Portal 只读查询子集（M194）：仅
  `GET /api/v1/network-portal/work-orders`、
  `GET /api/v1/network-portal/tasks`、
  `GET /api/v1/network-portal/technicians`、
  `GET /api/v1/network-portal/workbench`（计数/摘要）、
  `GET /api/v1/network-portal/capacity`（`dsp_capacity_counter` 按 networkId 聚合只读）。
  networkId **必须**从可信头 `X-Network-Context` 解析：接受 M188 `contextId` 形态
  `NETWORK|NETWORK|{uuid}`，或在校验当前主体对该网点持有 ACTIVE NetworkMembership 后接受
  纯 network UUID。**禁止**查询参数任意指定 networkId。上下文缺失、伪造或非成员返回
  `PORTAL_CONTEXT_INVALID`（403）。数据范围：该网点 ACTIVE NETWORK ServiceAssignment 对应的
  工单/任务；师傅列表为该网点 ACTIVE NetworkTechnicianMembership。能力：有效 NETWORK
  门户成员资格 + 既有 `networkTask.read` / `technician.readOwnNetwork`（NETWORK scope），
  跨网点失败关闭。**不**接受 Technician Feed §11、Admin work-queues §4、完整 product/03 页面集、
  Network Portal 写命令。
- §10 Network Portal 整改队列只读（M202 窄扩展）：仅
  `GET /api/v1/network-portal/correction-cases`（`status` 默认 `OPEN`、可选 `taskId`、
  `limit` 1～100 默认 50；`NetworkPortalPage` 形态安全摘要）与
  `GET /api/v1/network-portal/correction-cases/{correctionCaseId}`（CorrectionCase 详情形）。
  门禁：ACTIVE NetworkMembership + NETWORK scope `evidence.read`；数据仅限上下文网点
  ACTIVE NETWORK 责任任务上的整改。**不**接受 Admin 项目范围 cursor 队列语义、资质/产能写、
  异常队列。
- §10 Network Portal 运营异常队列只读（M203 窄扩展）：仅
  `GET /api/v1/network-portal/operational-exceptions`（`status` 默认 `OPEN`、可选 `taskId`、
  `severity`、`limit` 1～100 默认 50；`NetworkPortalPage` 形态安全摘要；`allowedActions` 恒为空）与
  `GET /api/v1/network-portal/operational-exceptions/{exceptionId}`（同形安全摘要）。
  门禁：ACTIVE NetworkMembership + NETWORK scope `operations.exception.read`；数据仅限上下文
  网点 ACTIVE NETWORK 责任任务上的运营异常。**不**接受 Portal ACK/resolve、Admin cursor 队列语义、
  资质/产能写。
- §10 Network Portal 本网点资质只读（M205 窄扩展）：仅
  `GET /api/v1/network-portal/technician-qualifications`（可选 `status`、`technicianProfileId`、
  `limit` 1～100 默认 50；`NetworkPortalPage` 形态安全摘要）与
  `GET /api/v1/network-portal/technician-qualifications/{qualificationId}`（同形安全摘要）。
  门禁：ACTIVE NetworkMembership + NETWORK scope `technician.readOwnNetwork`；数据仅限对本网点
  持有 ACTIVE NetworkTechnicianMembership 的师傅资质。**不**接受 Portal decide、FileObject、
  产能申请。
- §10 Network Portal 本网点师傅关系只读（M206 窄扩展）：仅
  `GET /api/v1/network-portal/technician-memberships`（可选 `status` 默认 `ACTIVE`、
  `technicianProfileId`、`limit` 1～100 默认 50；`NetworkPortalPage` 形态安全摘要，含真实
  `version`）与
  `GET /api/v1/network-portal/technician-memberships/{membershipId}`（同形安全摘要；
  `serviceNetworkId` 必须等于上下文）。
  门禁：ACTIVE NetworkMembership + NETWORK scope `technician.readOwnNetwork`；数据仅限
  `serviceNetworkId = contextNetworkId`。**不**接受操作员 NetworkMembership CRUD、Portal decide、
  产能申请。
- §10 Network Portal 工作台能力门控计数增强（M207 窄扩展）：复用
  `GET /api/v1/network-portal/workbench`（**不**新增路径）。基座门禁不变（ACTIVE
  NetworkMembership + NETWORK `networkTask.read`）。附加可选计数字段：
  `unassignedTechnicianTaskCount`（基座成功时始终返回）、
  `openCorrectionCaseCount`（NETWORK `evidence.read`）、
  `openOperationalExceptionCount`（NETWORK `operations.exception.read`）、
  `pendingQualificationCount`（NETWORK `technician.readOwnNetwork`）。
  enrichment 缺能力时 JSON **省略**对应属性（不得用 `null`/`0` 伪装无权限）；有能力且计数为 0
  时仍返回 0。enrichment 能力使用 `authorize`（非 `require`），缺能力不导致整页失败。
  **不**接受 SLA 风险计数、产能申请、Portal ACK/decide、新 capability。
- §10 Network Portal 产能页壳（M208 窄扩展）：**不**新增 HTTP 路径；消费既有
  `GET /api/v1/network-portal/capacity`（M194）。注册 Page Registry `NETWORK.CAPACITY`
  （能力仍为 NETWORK `networkTask.read`；catalog `page-registry-v15`）并交付 Admin Web
  `/network-portal/capacity` 只读列表（含 `version`）。工作台 capacity 深链至该页。
  **不**接受 `CapacityAdjustmentRequest`、产能写、未 Accepted 字段发明。
- §10 Network Portal 整改详情只读 UI（M209 窄扩展）：**不**新增 HTTP 路径；消费既有
  `GET /api/v1/network-portal/correction-cases/{correctionCaseId}`（M202 / ADR-040；
  响应 `CorrectionCase`）。Admin Web `/network-portal/corrections/:id` 只读详情（含
  source snapshot 与 `resubmissions[]`）；列表案例 ID 深链；任务代补深链。Page Registry
  仍归属 `NETWORK.CORRECTION.QUEUE`（catalog **保持** `page-registry-v15`）。
  **不**接受 Portal close/waive/ACK、新 pageId/capability。
- §10 Network Portal 运营异常详情只读 UI（M210 窄扩展）：**不**新增 HTTP 路径；消费既有
  `GET /api/v1/network-portal/operational-exceptions/{exceptionId}`（M203 / ADR-041；
  响应 `NetworkPortalExceptionItem`，`allowedActions` 恒为空）。Admin Web
  `/network-portal/exceptions/:id` 只读详情；列表异常 ID 深链；任务深链。Page Registry
  仍归属 `NETWORK.EXCEPTION.QUEUE`（catalog **保持** `page-registry-v15`）。
  **不**接受 Portal ACK/resolve、新 pageId/capability。
- §10 Network Portal 资质详情只读 UI（M211 窄扩展）：**不**新增 HTTP 路径；消费既有
  `GET /api/v1/network-portal/technician-qualifications/{qualificationId}`（M205 /
  ADR-043；响应 `NetworkPortalQualificationItem`）。Admin Web
  `/network-portal/qualifications/:id` 只读详情（含 decided*/version）；列表资质 ID 深链。
  Page Registry 仍归属 `NETWORK.QUALIFICATION`（catalog **保持** `page-registry-v15`）。
  **不**接受 Portal decide、FileObject、新 pageId/capability。
- §10 Network Portal 师傅关系详情只读 UI（M212 窄扩展）：**不**新增 HTTP 路径；消费既有
  `GET /api/v1/network-portal/technician-memberships/{membershipId}`（M206 / ADR-044；
  响应 `NetworkPortalMembershipItem`，含真实 version）。Admin Web
  `/network-portal/technicians/memberships/:id` 只读详情；师傅列表关系 ID 深链。
  Page Registry 仍归属 `NETWORK.TECHNICIAN.LIST`（catalog **保持** `page-registry-v15`）。
  **不**接受操作员 NetworkMembership、Portal decide、新 pageId/capability。
- §10 Network Portal 限定工单工作区（M213 窄接受）：新增
  `GET /api/v1/network-portal/work-orders/{workOrderId}/workspace`
  （`X-Network-Context` + NETWORK `networkTask.read` + ACTIVE NETWORK assignment 门禁；
  无责任 → `ACCESS_DENIED`）。响应薄 DTO `NetworkPortalWorkOrderWorkspace`
  （工单头 + ACTIVE 任务摘要；**不**复用 Admin workspace）。Page Registry
  `NETWORK.WORKORDER.WORKSPACE`（catalog → `page-registry-v16`）。Admin Web
  `/network-portal/work-orders/:id`。Core OpenAPI → `1.0.0`。
  **不**接受 Admin workspace 直调、客户 PII、INTEGRATION、Portal ACK、notifications、
  FieldOperation、完整 §6.1 SLA/Visit/表单区块发明。
- §10 Network Portal 工作区协作队列深链（M214 UI-only）：**不**新增 HTTP；在 M213 工作区
  上深链 `/tasks?taskId=`、`/corrections?taskId=`、`/exceptions?taskId=`，目标页水合
  `route.query.taskId` 并传入既有 list 过滤；工作区可 fan-in OPEN 整改/异常摘要（缺能力
  省略）。catalog 仍 `page-registry-v16`；OpenAPI 仍 `1.0.0`。
  **不**接受 SLA/Visit/表单 DTO 发明、PII、Portal ACK、notifications。
- §10 Network Portal 工作区预约/联系 fan-in（M215 UI-only）：**不**新增 HTTP；按工作区
  `taskIds` 客户端 fan-in
  `GET /network-portal/tasks/{taskId}/appointments`（M197）与
  `GET /network-portal/tasks/{taskId}/contact-attempts`（M199）；缺
  `networkPortal.manageAppointment` 时省略区块。catalog 仍 `page-registry-v16`；
  OpenAPI 仍 `1.0.0`。**不**接受 SLA/Visit/表单 DTO、PII、写控件嵌入工作区。
- §10 Network Portal 工作区当前师傅 fan-in（M216 UI-only）：**不**新增 HTTP；客户端
  fan-in `GET /network-portal/technicians`（M194）解析头/`tasks[].technicianId` →
  `displayName`/`membershipId`，深链师傅列表与 membership 详情；未指派深链
  `/tasks?taskId=`；可选展示既有 `Appointment.revisions[current].window`（禁止
  addressRef/note/PII）。缺 `technician.readOwnNetwork` 时省略师傅区块。catalog 仍
  `page-registry-v16`；OpenAPI 仍 `1.0.0`。**不**接受 SLA/Visit/表单 DTO、Admin
  workspace 复用、客户 PII。
- §10 Network Portal 目录页师傅 fan-in 与工作台基数深链（M217 UI-only）：**不**新增
  HTTP；工单/任务目录解析 `technicianId` → `displayName` 并展示既有非 PII 列；工作台
  ACTIVE 工单/任务/师傅计数深链对应目录；整改 `correctionTaskId` / 异常 `workOrderId`
  深链。catalog 仍 `page-registry-v16`；OpenAPI 仍 `1.0.0`。**不**接受列表预约 N+1
  fan-in、SLA/Visit/表单 DTO、PII、notifications。
- §11 Technician Portal Feed 子集（M195）：仅
  `GET /api/v1/technician/me/task-feed`（可选 `sinceCursor` 不透明游标；ACTIVE TECHNICIAN
  ServiceAssignment / TaskAssignment；撤权/结束时 tombstone 仅含 `taskId` +
  `invalidationReason`，不暴露敏感正文）、
  `GET /api/v1/technician/me/schedule`（对上述 ACTIVE 任务 fan-in 预约，非敏感字段）、
  `GET /api/v1/technician/me/sync-summary`（pending feed / 预约窗口 / tombstone 轻量计数；
  **不**含离线命令 runtime）。networkId **必须**从可信头 `X-Technician-Context` 解析：接受
  M188 `contextId` 形态 `TECHNICIAN|NETWORK|{uuid}`，或在校验当前主体对该网点持有 ACTIVE
  NetworkTechnicianMembership（及有效 TechnicianProfile）后接受纯 network UUID。**禁止**查询
  参数任意指定 networkId。上下文缺失、伪造或非师傅成员返回 `PORTAL_CONTEXT_INVALID`（403）。
  Feed 仅含 TECHNICIAN assignee = 当前 principal（或 TechnicianProfile 关联 principal）的责任。
  能力：有效师傅成员资格 + 既有 `task.readAssigned`（NETWORK scope）。**不**接受
  `GET /mobile-work-packages/{id}/status`（离线工作包 runtime 未 Accepted）、完整 Technician App、
  Network Portal 写命令。
- §11 Technician Portal Feed Accepted 字段展示（M218 UI-only）：**不**新增 HTTP；在 M195
  shell 上展示 task-feed/schedule/sync-summary 既有非 PII 字段与 asOf/networkId；Feed
  `taskId` 深链 schedule?taskId=；SyncSummary 计数深链 Feed/日程；可选 sinceCursor 增量。
  catalog 仍 `page-registry-v16`；OpenAPI 仍 `1.0.0`。**不**接受离线工作包、TASK.DETAIL、
  MESSAGE、PII、GPS/上传。
- §11 Technician Portal `TECHNICIAN.ME` 页壳（M219 UI-only）：**不**新增 HTTP；独立路由
  `/technician-portal/me` 消费 Accepted `GET /me`、`/me/contexts`、`/me/capabilities`；
  修正此前 ME→sync-summary 别名。catalog 仍 `page-registry-v16`；OpenAPI 仍 `1.0.0`。
  **不**接受 PROFILE/TASK.DETAIL/MESSAGE、离线工作包、PII。
- §10 Network Portal 队列/列表 Accepted 字段展示（M220 UI-only）：**不**新增 HTTP；整改/
  异常/资质/师傅列表与任务目录展示既有非 PII 字段；`correctionTaskId`/`handlingTaskId`/
  `workOrderId` 门户内深链；异常详情 `handlingTaskId` 深链。catalog 仍 `page-registry-v16`；
  OpenAPI 仍 `1.0.0`。**不**接受 ACK/decide、Admin Review 深链、SLA/Visit/表单、notifications。
- §10 Network Portal 工作区薄 SLA 摘要（M221 / ADR-059）：扩展
  `GET /api/v1/network-portal/work-orders/{workOrderId}/workspace` 可选字段
  `slaSummary.{openCount,breachedCount}`；NETWORK `sla.read` soft-gate（缺能力省略属性，
  不得用 0 伪装无权限）；仅计本网点 ACTIVE `taskIds` 上 RUNNING/BREACHED。
  Core OpenAPI → `1.0.1`。catalog 仍 `page-registry-v16`；Flyway 仍 100/102。
  **不**接受 Visit/表单摘要、工作台 SLA 风险计数、Admin workspace 复用、PII、SLA 详情/deeplink。
- §10 Network Portal 工作区 Visit/表单提交摘要（M222 / ADR-060）：扩展同一 workspace 可选
  `visits`（`$ref` `WorkOrderWorkspaceVisitSummary`；NETWORK `visit.read`）与
  `formSubmissions`（`$ref` `WorkOrderWorkspaceFormSubmissionSummary`；NETWORK `form.read`）；
  缺能力省略属性（不得用空数组伪装）；Visit 另按可信 networkId 过滤；表单仅 ACTIVE taskIds。
  Core OpenAPI → `1.0.2`。catalog 仍 `page-registry-v16`；Flyway 仍 100/102。
  **不**接受 definition/values、Evidence 摘要、Admin workspace 复用、独立 NP Visit/表单列表 API、
  工作台 SLA 风险、notifications。
- §10 Network Portal 工作区 Evidence 槽位/资料项摘要（M223 / ADR-061）：扩展同一 workspace 可选
  `evidenceSlots`（`$ref` `WorkOrderWorkspaceEvidenceSlotSummary`）与
  `evidenceItems`（`$ref` `WorkOrderWorkspaceEvidenceItemSummary`）；
  共用 NETWORK `evidence.read` soft-gate（缺能力同时省略两属性，不得用空数组伪装）；
  仅 ACTIVE taskIds；单任务未解析跳过。Core OpenAPI → `1.0.3`。catalog 仍
  `page-registry-v16`；Flyway 仍 100/102。
  **不**接受 Admin workspace 复用、独立 NP Evidence 列表、缩略图/下载、Revision 图、
  definition JSON、工作台 SLA 风险、notifications。
- §10 Network Portal 工作台薄 SLA 风险计数（M224 / ADR-062）：扩展
  `GET /api/v1/network-portal/workbench` 可选 `slaSummary`（`$ref`
  `NetworkPortalWorkOrderWorkspaceSlaSummary`）；NETWORK `sla.read` soft-gate；
  跨本网点全部 ACTIVE taskIds 聚合 RUNNING/BREACHED。Core OpenAPI → `1.0.4`。
  catalog 仍 `page-registry-v16`；Flyway 仍 100/102。
  **不**接受即将超时时间窗、SLA 详情/deeplink、notifications、Portal ACK。

**仍为设计草案**：§3 中 `application-context`/`notifications`、§4 工作台与队列、§5 其余 section、
§6 其余专项队列、§7 中 `VEHICLE`/`CHARGER` 与全文索引搜索、§8 ORGANIZATION 组织树共享与
Network/Technician SavedView、§9 非 Admin Portal UI Preference、§10 未列入本切片的其余
Network 查询、§11 离线工作包 status 与导出分析等。
不得在未再接受前实现。

## 1. 目标

本文件定义面向 Admin、Network 和 Technician Portal 的只读组合查询与个人偏好契约。它不创建第二套领域命令；所有写业务动作仍调用 API-02～05/API-07 的领域命令和 allowed-actions。本文件的 SavedView/UI Preference 写入只修改用户展示偏好，不推进履约业务。

## 2. 通用查询元数据

所有投影查询返回：

```json
{
  "data": {},
  "meta": {
    "asOf": "2026-07-13T08:00:00Z",
    "projectionCheckpoint": "WO-PROJ-991",
    "freshnessStatus": "FRESH",
    "scopeVersion": 42,
    "queryId": "Q-01J...",
    "nextCursor": null
  }
}
```

`freshnessStatus`：`FRESH/LAGGING/UNKNOWN/REBUILDING`。查询成功不代表可以执行动作；客户端继续调用资源 allowed-actions。

## 3. 当前主体与导航

| 方法与路径 | 用途 |
|---|---|
| `GET /api/v1/me/application-context` | 当前 tenant、Portal、组织/网点、scopeVersion、feature 摘要 |
| `GET /api/v1/me/navigation` | 当前 Portal 可见 pageId/route/label/order/badgeQueryRef |
| `GET /api/v1/me/recent-resources` | 最近访问对象（受当前权限重新过滤） |
| `GET /api/v1/me/notifications` | 站内通知摘要与深链 |

Navigation 只改善体验，不作为路由或 API 授权凭证。客户端不能提交 role name 选择菜单。

Navigation 返回 `navigationCatalogVersion` 和已注册 pageId；route 只从客户端/服务端共同发布的目录解析，不接受服务端下发任意外部 URL 或可执行组件名。客户端未知 pageId 安全忽略并记录兼容性指标。

## 4. 工作台与队列

| 方法与路径 | 用途 | 关键参数 |
|---|---|---|
| `GET /api/v1/me/workbench` | Portal/角色模板工作台 | scope、window |
| `GET /api/v1/work-queues` | 当前主体可用队列目录 | portal、group |
| `GET /api/v1/work-queues/{queueCode}/items` | 队列项 | cursor、sort、filters |
| `GET /api/v1/work-queues/{queueCode}/count` | 轻量计数 | scope、window |

Queue item 最小模型：

```json
{
  "queueItemId": "TASK:T-100",
  "resourceRef": {"type": "Task", "id": "T-100", "version": 7},
  "workOrderRef": {"id": "WO-1", "displayNo": "SO-2026-1"},
  "title": "审核安装资料",
  "subtitle": "比亚迪 / 青岛 / 安装",
  "severity": "WARNING",
  "dueAt": "2026-07-13T10:00:00+08:00",
  "badges": ["资料待审核", "SLA 2小时"],
  "assigneeSummary": "我的任务",
  "updatedAt": "2026-07-13T07:58:00Z"
}
```

队列定义由服务端版本化管理，客户端不能把任意字段/SQL 作为 queue query。

## 5. 工单查询与工作区

| 方法与路径 | 用途 |
|---|---|
| `GET /api/v1/work-orders` | 工单列表投影，沿用 API-02 |
| `GET /api/v1/work-orders/{id}/workspace` | 工单工作区组合快照 |
| `GET /api/v1/work-orders/{id}/workspace/sections/{section}` | 大区块按需加载 |
| `GET /api/v1/work-orders/{id}/activity-summary` | 最近关键业务事件 |

M93 接受的 activity-summary 不引入未定义的“关键事件”分类：它返回时间线按业务时间倒序的
最近 5 条（可调 1～20），不接受 cursor。完整分页继续使用 `/timeline`。

Workspace 顶层：

```text
header
currentTaskSummary
customer/location/vehicle/device sections（字段策略后）
serviceAssignmentSummary
slaSummary
exceptionSummary
sectionAvailability
allowedActionLink
sourceVersions
```

`section` 枚举：`TASKS`、`APPOINTMENTS_VISITS`、`FORMS_EVIDENCE`、`REVIEWS_CORRECTIONS`、`INTEGRATION`、`FACTS_CALCULATIONS`、`TIMELINE_AUDIT`。

组合响应只引用权威对象版本；它自身是可重建投影，不能被 PATCH。

## 6. 专项队列查询

| 方法与路径 | 用途 |
|---|---|
| `GET /api/v1/dispatch-requests` | 派单/改派队列和摘要 |
| `GET /api/v1/review-cases` | 审核队列，按 target/type/SLA/assignee |
| `GET /api/v1/correction-cases` | 整改跟踪队列 |
| `GET /api/v1/operational-exceptions` | 异常队列 |
| `GET /api/v1/outbound-deliveries` | 回传/通知交付队列 |
| `GET /api/v1/inbound-envelopes` | 入站 Envelope 授权队列（M158） |
| `GET /api/v1/fact-extraction-runs` | 事实冲突/失败队列 |
| `GET /api/v1/calculation-runs` | SHADOW 试算队列 |
| `GET /api/v1/sla-instances` | SLA 风险/超时队列 |

这些端点复用各领域的查询模型和数据范围；本 API 只规定 Portal 需要的筛选、分页和 freshness，不转移实体所有权。

### 6.1 `GET /api/v1/inbound-envelopes`（M158 Accepted）

- 能力：`integration.readInbound`；实时 TENANT/PROJECT/REGION/NETWORK 项目范围；
- 仅返回 `projectId IS NOT NULL` 的 Envelope；null-project 可见性仍为草案；
- 筛选：`projectId`、`processingStatus`（默认 `RECEIVED`）、`messageType`、
  `resultType`、`resultId`、`canonicalMessageId`、`cursor`、`limit`（1～100）；
- 排序：`receivedAt DESC, inboundEnvelopeId DESC`；游标绑定 scopeDigest 与全部筛选；
- 响应页：`{ items, nextCursor, asOf }`；
- 条目安全字段：身份、messageType、externalMessageId、signature/processing 状态、
  mapping/canonical/result 引用、receivedAt/completedAt、correlationId；
- 禁止：raw/canonical payload digest、对象存储引用、签名原文、nonce、凭据。

## 7. 全局搜索

| 方法与路径 | 用途 |
|---|---|
| `GET /api/v1/search?q=&types=` | 当前 tenant/scope 内受控搜索 |

### 7.1 Admin 受控搜索（M192 Accepted）

- Portal：`ADMIN`；其他 Portal 不得调用本切片。
- 接受 type：`WORK_ORDER`、`EXTERNAL_ORDER`、`NETWORK`、`TECHNICIAN`。
  未支持 type（含 `VEHICLE`/`CHARGER`）→ `SEARCH_TERM_NOT_ALLOWED`（422）。
- `q`：最短 2（UUID / 精确外部单号除外）；每 type 结果上限小（如 10），总上限；超时失败关闭。
- 手机号：若 `q` 呈完整手机号形态，仅允许末四位精确；禁止在响应/日志中回显完整敏感 `q`。
- 匹配语义（无全文索引）：工单 UUID 或授权范围内 `externalOrderCode` 精确；
  `EXTERNAL_ORDER` 同码匹配但结果 type 区分；网点 code/name 前缀；师傅 displayName 前缀或
  关联主体 `employeeNumber` 精确（经 identity/network 授权端口）。
- 结果：`resourceRef`、`type`、`primaryLabel`、`maskedSecondaryLabel`、`matchReason`、`deepLink`。
- 能力：`search.read`（HIGH）+ 各 type 对应读能力；缺 type 能力省略该 type，不整请求 403。
- **不**接受独立搜索索引平台、`VEHICLE`/`CHARGER`、Network/Technician Portal 搜索 UI。

完整设计草案仍可包含 VEHICLE/CHARGER；本切片外类型在未再接受前不得实现。

## 8. Saved View

| 方法与路径 | 用途 |
|---|---|
| `GET /api/v1/me/saved-views?pageId=` | 个人 + 当前主体可见的共享视图 |
| `POST /api/v1/me/saved-views` | 创建个人视图（初始 `visibility=PRIVATE`） |
| `PUT /api/v1/me/saved-views/{id}` | 更新名称/列/排序/受控筛选（仅 owner） |
| `DELETE /api/v1/me/saved-views/{id}` | 删除个人视图（仅 owner） |
| `POST /api/v1/saved-views/{id}:share` | 设置可见性：`ROLE` / `TENANT` / `PRIVATE`（受控能力） |

### 8.1 共享 SavedView（M191 Accepted）

- 仅 owner 可 share；跨主体按 `RESOURCE_NOT_FOUND`。
- body：`visibility`（`PRIVATE` \| `ROLE` \| `TENANT`）与可选 `sharedScopeRef`；
  `ROLE` 时 `sharedScopeRef` 必须为同租户有效 `roleId`；`TENANT`/`PRIVATE` 时必须为空。
- 分享到 `ROLE`/`TENANT` 需要 `preference.shareSavedView`（HIGH）；owner 将可见性收回
  `PRIVATE` **不**要求该 capability。
- 共享只复制查询定义可见性；不授予页面、字段或数据能力；列表含共享项不意味可执行业务查询。
- 并发：`If-Match` 绑定 `aggregateVersion`；冲突 `VERSION_CONFLICT`。
- 本切片不接受 `ORGANIZATION`、独立 `:unshare` 路径、Network/Technician Portal。

View 保存 filter AST、列、排序和密度，不保存任意 SQL、访问 token 或完整敏感搜索值。每次执行重新鉴权并与当前字段目录/Schema 迁移。

## 9. 用户 UI 偏好

| 方法与路径 | 用途 |
|---|---|
| `GET /api/v1/me/ui-preferences?portal=` | 当前 Portal 偏好 |
| `PUT /api/v1/me/ui-preferences` | 更新主题、密度、语言、列宽等 |
| `DELETE /api/v1/me/ui-preferences/{key}` | 恢复默认 |

偏好不能关闭事务通知、安全告警、必填字段、数据脱敏或高风险确认。

### 9.1 Admin UI Preference（M190 Accepted）

本切片仅接受 Portal=`ADMIN`。`portal` 查询/请求体必须为 `ADMIN`；`NETWORK`/`TECHNICIAN` 或其他值
返回 `VALIDATION_FAILED`（失败关闭）。任意已认证主体仅可读写**自己的**偏好（tenant + principal
作用域）；跨主体按不存在/不可达处理，不新增 Capability。

**允许键（白名单；未知键 `UI_PREFERENCE_KEY_NOT_ALLOWED` 或 `VALIDATION_FAILED`）**：

| key | value 形态 | 说明 |
|---|---|---|
| `theme` | `LIGHT` \| `DARK` \| `SYSTEM` | 外观主题 |
| `density` | `COMFORTABLE` \| `COMPACT` | 列表/表单密度 |
| `locale` | BCP-47 字符串（如 `zh-CN`） | 界面语言；本切片采用 `locale`，不另设 `language` |
| `reduceMotion` | boolean | 减少动画 |
| `defaultSavedViews` | `{ "<pageId>": "<uuid>" \| null, ... }` | 每页默认 SavedView；pageId 限于已 Accepted 个人 SavedView 页面 |
| `columnWidths` | `{ "schemaVersion": n, "pages": { "<pageId>": { "<columnId>": widthPx } } }` | 可选列宽；schemaVersioned；pageId 同上 |

**明确禁止（失败关闭）**：任何试图关闭安全确认、隐藏必填字段、绕过脱敏、禁用事务/安全通知的键或值。
白名单外键一律拒绝。

并发：按 preference_key 乐观版本（`aggregateVersion` / 可选 `expectedVersion`）；冲突 `VERSION_CONFLICT`。

## 10. Network 查询

| 方法与路径 | 用途 | 接受状态 |
|---|---|---|
| `GET /api/v1/network-portal/workbench` | 当前 NetworkMembership 工作台（计数/摘要；M207 能力门控 enrichment） | M194 / M207 Accepted |
| `GET /api/v1/network-portal/work-orders` | 当前 ACTIVE assignment 工单 | M194 Accepted |
| `GET /api/v1/network-portal/work-orders/{workOrderId}/workspace` | 限定工单工作区薄快照 | M213 Accepted |
| `GET /api/v1/network-portal/tasks` | 本网点 Task | M194 Accepted |
| `GET /api/v1/network-portal/technicians` | 本网点师傅/能力/资质摘要 | M194 Accepted |
| `GET /api/v1/network-portal/capacity` | 本网点容量和派单状态（M208 注册 `NETWORK.CAPACITY` 页壳；无新路径） | M194 Accepted；M208 页壳 |
| `GET /api/v1/network-portal/correction-cases` | 本网点整改队列安全摘要 | M202 Accepted |
| `GET /api/v1/network-portal/correction-cases/{correctionCaseId}` | 本网点整改详情（M209 Admin Web 只读详情页） | M202 Accepted；M209 详情 UI |
| `GET /api/v1/network-portal/operational-exceptions` | 本网点运营异常队列安全摘要 | M203 Accepted |
| `GET /api/v1/network-portal/operational-exceptions/{exceptionId}` | 本网点运营异常详情（M210 Admin Web 只读详情页） | M203 Accepted；M210 详情 UI |
| `GET /api/v1/network-portal/technician-qualifications` | 本网点师傅资质列表安全摘要 | M205 Accepted |
| `GET /api/v1/network-portal/technician-qualifications/{qualificationId}` | 本网点师傅资质详情（M211 Admin Web 只读详情页） | M205 Accepted；M211 详情 UI |
| `GET /api/v1/network-portal/technician-memberships` | 本网点师傅关系列表安全摘要（含 version） | M206 Accepted |
| `GET /api/v1/network-portal/technician-memberships/{membershipId}` | 本网点师傅关系详情（M212 Admin Web 只读详情页） | M206 Accepted；M212 详情 UI |

networkId 从可信应用上下文解析；拥有多个 membership 时使用经授权的 `X-Network-Context`，不能在查询参数任意指定。详见 §0 M194 / M202 / M203 / M205 / M206 / M207 / M208。

## 11. Technician Feed 与工作包状态

| 方法与路径 | 用途 | 接受状态 |
|---|---|---|
| `GET /api/v1/technician/me/task-feed` | 当前 TaskAssignment / ServiceAssignment Feed 与增量 cursor | M195 Accepted |
| `GET /api/v1/technician/me/schedule` | Appointment 日程 | M195 Accepted |
| `GET /api/v1/technician/me/sync-summary` | 待处理 feed / 预约窗口 / tombstone 轻量计数 | M195 Accepted |
| `GET /api/v1/mobile-work-packages/{id}/status` | 工作包有效性和服务器版本 | 仍为设计草案（离线 runtime 未 Accepted） |

Feed 支持 `sinceCursor` 增量；删除/撤权使用 tombstone，仅含 taskId 和 invalidationReason，不继续暴露敏感正文。上下文与能力见 §0 M195。

## 12. 批量 Operation 查询

| 方法与路径 | 用途 |
|---|---|
| `GET /api/v1/batch-operations/{id}` | 总量、dry-run、逐项状态和结果下载 |
| `GET /api/v1/batch-operations/{id}/items` | 逐项分页 |

创建批量动作由对应领域 API 定义。本查询投影不能修改 item 结果。

## 13. 受控运营分析

| 方法与路径 | 用途 |
|---|---|
| `GET /api/v1/analytics/metric-definitions` | 当前可见指标目录、版本和口径 |
| `POST /api/v1/analytics/queries:execute` | 使用 metricCode、维度、窗口、受控 filters 查询 |
| `POST /api/v1/analytics/queries:drill-down` | 生成同口径资源队列/列表查询 |
| `POST /api/v1/analytics/exports` | 异步受控导出 |

AnalyticsQuery 不接受表达式或 SQL，只接受已发布 metric/dimension/filter 目录。响应返回 metricVersion、window、timezone、value、numerator/denominator（允许时）、sampleSize、qualityFlags、asOf 和 drillDownQueryRef。

MVP 不从 SHADOW CalculationRun 推断正式收入/成本/毛利；试算汇总必须携带 `mode=SHADOW` 和方向，Finance 指标在 FORMAL_SETTLEMENT 启用前不可用。

## 14. 分页与过滤

- 默认使用 opaque cursor；
- sort 必须来自端点允许目录并包含稳定 tie-breaker；
- filter 使用受控字段、操作符和枚举；
- 服务端返回 appliedFilters/ignoredFilters，未知必需 filter 返回 422；
- count 可以是 EXACT/ESTIMATED/UNAVAILABLE，并明确类型；
- 查询 URL 长度超限时使用 `POST /queries:execute` 的只读受控 QuerySpec，不接受 SQL。

## 15. 缓存

- ETag/If-None-Match 用于只读资源；
- `Cache-Control: private`，敏感页面默认 no-store；
- 缓存键包含 tenant、subject/scopeVersion、portal 和 fieldPolicyVersion；
- 权限/改派变化后服务端实时查询仍必须拒绝，不能信任旧缓存；
- CDN 不缓存用户工单/资料/金额响应。

## 16. 错误码

| 错误码 | HTTP | 含义 |
|---|---:|---|
| `PORTAL_CONTEXT_INVALID` | 403 | 当前主体不能使用请求 Portal/网点上下文 |
| `SAVED_VIEW_SCHEMA_OUTDATED` | 409 | 视图字段已变化，需迁移/重置 |
| `QUERY_FILTER_NOT_ALLOWED` | 422 | 字段或操作符不允许 |
| `UI_PREFERENCE_KEY_NOT_ALLOWED` | 422 | UI 偏好键不在白名单或被禁止 |
| `PROJECTION_REBUILDING` | 200/503 | 可返回降级数据或暂不可用 |
| `SEARCH_TERM_NOT_ALLOWED` | 422 | 敏感/过宽搜索不允许 |
| `WORK_PACKAGE_INVALIDATED` | 409 | assignment/authority/config 已变化 |
| `METRIC_NOT_AVAILABLE` | 404/422 | 指标未发布、无权限或 feature 未启用 |
| `ANALYTICS_DIMENSION_NOT_ALLOWED` | 422 | 指标不允许该维度/筛选 |

## 17. 安全

- 所有投影在查询时应用当前 ScopePredicate/FieldPolicy；
- 投影中的 assignee/network/permission 摘要不是执行授权真相；
- pageId、saved view、deep link 和 queueCode 都不是 capability；
- 敏感搜索、导出、下载和原始报文另行增强审计；
- 查询日志记录 queryId/字段目录，不记录完整敏感 filter 值。
