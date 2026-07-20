---
title: M408 Network 分配候选预约日程冲突摘要
version: 0.1.0
status: Implemented
milestone: M408
lastUpdated: 2026-07-20
---

# M408 Network 分配候选预约日程冲突摘要

## 1. 目标

在 M407 分配候选摘要上补充可证明的预约日程冲突信号：未完成预约数量、与当前任务预约窗口重叠。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.74** `NetworkPortalAssignCandidateItem` 增加 `upcomingAppointmentCount` / `scheduleConflictSummary` / `scheduleOverlap` |
| ReadModel | fan-in 本网点 ACTIVE 任务的 PROPOSED/CONFIRMED 预约；按师傅归属统计与重叠检测 |
| Network Web | 分配抽屉展示日程摘要与影响区提示 |

## 3. 明确未实现

- 距离 / 路况 / 推荐评分
- 细粒度日历可视化
- 产品负责人视觉金标

## 4. 权限与边界

- 仍要求 `networkTask.read` + `technician.readOwnNetwork`
- 仅使用本网点 ACTIVE 责任任务上的预约事实；距离字段继续不伪造
