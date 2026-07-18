---
title: ADR-082：Technician Portal 联系历史安全摘要
status: Accepted
date: 2026-07-18
---

# ADR-082：Technician Portal 联系历史安全摘要

## 背景

M243 已建立当前师傅责任任务详情，但尚不能看到既有联系事实。完整 `ContactAttempt` 含
`contactedPartyRef`、自由文本、录音引用和操作者标识，不应直接复用到 Technician 详情。现有联系写命令
又强制 `contactedPartyRef`，而当前详情没有已接受的合规联系人引用来源，不能让客户端手填或服务端猜测。

## 决策

1. 在 M243 详情响应增加 `contactAttempts[]`，不新增 HTTP 路径；
2. Appointment 模块提供 Technician 专用只读 fan-in，只白名单读取联系事实 ID、taskId、渠道、开始/结束、
   标准结果、下次联系时间和创建时间；
3. 明确禁止返回 `contactedPartyRef`、`note`、`recordingRef`、`actorId`，SQL 层不得 `SELECT *`；
4. 继续复用 M243 的可信上下文、ACTIVE 成员、`task.readAssigned` 与当前责任门禁；其他任务仍统一 404；
5. Core OpenAPI 升至 `1.0.18`；Flyway 保持 100/102，Page Registry 保持 `page-registry-v16`；
6. 不接受联系写入。只有联系人引用来自已接受权威数据且联系字段策略明确后，才能通过 Technician 专用
   写适配器委托既有 Appointment 命令。

## 明确不接受

- 联系对象引用、电话、地址、自由文本、录音和操作者标识；
- 联系写命令、预约写命令或客户端猜测联系人；
- Visit、表单、Evidence、整改、离线工作包和通知。

## 后果

师傅能判断任务是否已联系、最近渠道/结果以及下次联系时间，同时完整联系事实仍留在原有授权边界内；
后续写入不会因缺少联系人权威来源而产生不可纠正的错误事实。
