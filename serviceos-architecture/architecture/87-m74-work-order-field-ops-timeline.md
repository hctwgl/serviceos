---
title: M74 工单现场履约时间线事件合并
status: Implemented
milestone: M74
---

# M74 工单现场履约时间线事件合并

## 1. 目标

在 M73 已建立的独立 `readmodel` 工单时间线投影上，合并已发布的 Appointment / Visit /
ContactAttempt 公开事件，使 `GET /api/v1/work-orders/{workOrderId}/timeline` 覆盖现场履约链的
联系、预约与到离场事实。不改变查询授权、分页、freshness 语义，也不回写领域聚合。

## 2. 模块与事实边界

- 继续由同一 Inbox 消费者 `readmodel.work-order-core-timeline.v1` 写入 `rdm_work_order_timeline_entry`；
- 现场事件载荷已携带权威 `workOrderId` / `projectId`，不新增跨模块表查询端口；
- 投影仍通过 `workorder::api` 校验工单存在与 Project 一致；tenant、aggregate/resource、Project
  或发生时间错配时 Inbox 与投影整体回滚；
- 不保存 `contactedPartyRef`、`noShowPartyRef`、`note`、GPS、evidenceRefs、operationRefs、
  自由文本、payload 或签名凭据；只保留稳定编码、资源身份、主体引用和双时间。

## 3. 支持的现场事件

在 M73 核心执行事件之外，本里程碑仅接受已发布 v1：

- `contact.attempt.recorded@v1` → category `CONTACT_ATTEMPT`，resource `ContactAttempt`，
  `resourceCode=channel`，`outcomeCode=resultCode`，`actorId` 取载荷；
- `appointment.proposed/confirmed/rescheduled/cancelled/no-show-marked@v1` → category
  `APPOINTMENT`，resource `Appointment`，`resourceCode=appointmentType`；
  propose/confirm 用 `status`，reschedule 用稳定 outcome `RESCHEDULED`，cancel/no-show 用
  `reasonCode`；
- `visit.checked-in/checked-out/interrupted@v1` → category `VISIT`，resource `Visit`，
  `actorId=technicianId`；check-in 用 `status`，check-out 用 `resultCode`，interrupt 用
  `exceptionCode`。

## 4. 查询、授权与分页

与 M73 完全一致：

- 每页复用 `workOrder.read` 实时 Project Scope；
- `(occurredAt DESC, timelineEntryId DESC)` 游标绑定 workOrderId；
- `freshnessStatus` 仍为显式 `UNKNOWN`，不伪造 Broker checkpoint。

Core OpenAPI 将 timeline 的 `category` / `eventType` / `resourceType` 从封闭 `enum` 调整为
`x-extensible-enum`，以便后续领域事件可兼容扩展；客户端必须容忍未知值。

## 5. 数据库

V072 以 expand 方式放宽 `ck_rdm_work_order_timeline_category`，允许
`APPOINTMENT` / `VISIT` / `CONTACT_ATTEMPT`。投影仍可清空重建，无跨模块外键。

## 6. 明确未实现

Evidence/Review、Delivery、SLA、OperationalException、试算/结算事件合并，correlation 展开、
敏感字段二次授权、投影重建作业、Broker checkpoint、搜索、导出和 Portal 不在 M74。
Appointment/Visit 写命令、状态机和 GPS 策略语义不变。
