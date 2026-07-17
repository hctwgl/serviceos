---
title: 应用工作区、队列与用户偏好 HTTP API
version: 0.1.0
status: Accepted
---

# 应用工作区、队列与用户偏好 HTTP API

## 0. 接受范围（M85 / M87 / M88 / M89 / M90 / M91 / M92 / M93 / M94 / M95 / M96 / M97 / M98 / M99 / M100 / M158）

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

**仍为设计草案**：§3 导航、§4 工作台与队列、§5 其余 section、
§6 其余专项队列与 §7～§11 搜索/偏好/导出等。不得在未再接受前实现。

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

支持 type：WORK_ORDER、EXTERNAL_ORDER、VEHICLE、CHARGER、NETWORK、TECHNICIAN。手机号仅允许末四位/授权精确搜索；响应不回显完整敏感 query。

搜索结果：resourceRef、type、primaryLabel、maskedSecondaryLabel、matchReason、deepLink。服务端先应用 ScopePredicate，再返回结果。

## 8. Saved View

| 方法与路径 | 用途 |
|---|---|
| `GET /api/v1/me/saved-views?pageId=` | 个人/共享视图 |
| `POST /api/v1/me/saved-views` | 创建个人视图 |
| `PUT /api/v1/me/saved-views/{id}` | 更新名称/列/排序/受控筛选 |
| `DELETE /api/v1/me/saved-views/{id}` | 删除个人视图 |
| `POST /api/v1/saved-views/{id}:share` | 共享给角色/组织（受控能力） |

View 保存 filter AST、列、排序和密度，不保存任意 SQL、访问 token 或完整敏感搜索值。每次执行重新鉴权并与当前字段目录/Schema 迁移。

## 9. 用户 UI 偏好

| 方法与路径 | 用途 |
|---|---|
| `GET /api/v1/me/ui-preferences?portal=` | 当前 Portal 偏好 |
| `PUT /api/v1/me/ui-preferences` | 更新主题、密度、语言、列宽等 |
| `DELETE /api/v1/me/ui-preferences/{key}` | 恢复默认 |

偏好不能关闭事务通知、安全告警、必填字段、数据脱敏或高风险确认。

## 10. Network 查询

| 方法与路径 | 用途 |
|---|---|
| `GET /api/v1/network-portal/workbench` | 当前 NetworkMembership 工作台 |
| `GET /api/v1/network-portal/work-orders` | 当前 ACTIVE assignment 工单 |
| `GET /api/v1/network-portal/tasks` | 本网点 Task |
| `GET /api/v1/network-portal/technicians` | 本网点师傅/能力/资质摘要 |
| `GET /api/v1/network-portal/capacity` | 本网点容量和派单状态 |

networkId 从可信应用上下文解析；拥有多个 membership 时使用经授权的 `X-Network-Context`，不能在查询参数任意指定。

## 11. Technician Feed 与工作包状态

| 方法与路径 | 用途 |
|---|---|
| `GET /api/v1/technician/me/task-feed` | 当前 TaskAssignment Feed 与增量 cursor |
| `GET /api/v1/technician/me/schedule` | Appointment 日程 |
| `GET /api/v1/technician/me/sync-summary` | 待同步/冲突/失败摘要 |
| `GET /api/v1/mobile-work-packages/{id}/status` | 工作包有效性和服务器版本 |

Feed 支持 `sinceCursor` 增量；删除/撤权使用 tombstone，仅含 taskId 和 invalidationReason，不继续暴露敏感正文。

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
