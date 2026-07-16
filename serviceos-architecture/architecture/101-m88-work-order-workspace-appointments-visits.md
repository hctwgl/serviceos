---
title: M88 工单工作区预约与到访区块
status: Implemented
milestone: M88
---

# M88 工单工作区预约与到访区块

## 1. 目标

扩展 API-06 §5 `workspace/sections/{section}` Accepted 范围，增加
`APPOINTMENTS_VISITS` 实时组合按需加载。

## 2. 接受范围

- API-06 §5 新增 `APPOINTMENTS_VISITS`（仍不含其余 section / activity-summary）；
- 复用 `workOrder.read` 入口鉴权；Visit / Appointment 分别经 `visit.read` /
  `appointment.read` 装载，缺权时该半边为 null，不把整个工作区打成 403；
- 载荷不含 GPS、地址引用、联系人、note 等敏感/自由文本字段。

## 3. 组合事实

| 子集 | 来源 |
|---|---|
| visits | `fieldwork::api` `VisitService.listByWorkOrder` |
| appointments | 工单 Task 列表后 `appointment::api` `AppointmentService.listByTask` 扇出 |

顶层 `sectionAvailability.APPOINTMENTS_VISITS`：缺两边读权 → UNAVAILABLE；两边空 → EMPTY；否则 AVAILABLE。

limit 分别截断 visits / appointments；本切片不提供跨 Task 深分页 cursor（cursor 必须为空）。

## 4. 契约

Core OpenAPI **0.58.0**。无新 Flyway。

## 5. 明确未实现

其余 section、contact-attempts、队列/SavedView、Portal、工单级 Appointment 列表端口、区块持久化投影。
