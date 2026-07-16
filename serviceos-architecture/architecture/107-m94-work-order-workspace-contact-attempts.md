---
title: M94 工单工作区联系尝试摘要
status: Implemented
milestone: M94
---

# M94 工单工作区联系尝试摘要

## 1. 目标

补齐 M88 `APPOINTMENTS_VISITS` 明确未实现的联系历史，在既有区块增加
`contactAttempts` 实时摘要。

## 2. 接受范围

- 经工单 Task 扇出 `AppointmentService.listContactAttempts`；
- 复用 `appointment.read` 与实时 Project/Network Scope；缺权时 contactAttempts 与
  appointments 均为 null，不把整个区块打成 403；
- 按 `(startedAt DESC, contactAttemptId ASC)` 排序并独立应用 limit；
- 摘要不含 `contactedPartyRef`、note、recordingRef、actorId；
- 顶层 APPOINTMENTS_VISITS availability 同时考虑 visits、appointments、contactAttempts：
  有任一数据为 AVAILABLE；无数据但有缺权为 UNAVAILABLE；全部有权且空为 EMPTY。

## 3. 契约

Core OpenAPI **0.64.0**。无新 Flyway，保持 V080 / 82 migrations。

## 4. 明确未实现

工单级 ContactAttempt 列表端口、cursor、联系对象/录音/自由文本展示、
FACTS_CALCULATIONS、customer/location、队列/SavedView、Portal。
