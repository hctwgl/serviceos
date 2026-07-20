---
title: M407 Network 分配师傅候选摘要
version: 0.1.0
status: Implemented
milestone: M407
lastUpdated: 2026-07-20
---

# M407 Network 分配师傅候选摘要

## 1. 目标

关闭 Network 分配抽屉 UI_DATA_GAP 中可由本网点事实证明的部分：开放任务数、资质摘要、网点产能口径与可分配警告。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.73** `GET /network-portal/tasks/{taskId}/assign-candidates` |
| ReadModel | 聚合 ACTIVE 师傅、ACTIVE 责任任务数、资质状态、产能计数 |
| Network Web | 工作台/工作区分配抽屉消费候选摘要 |

## 3. 明确未实现

- 距离、日程冲突、推荐评分解释
- 师傅个人今日预约卡片
- 产品负责人视觉金标

## 4. 权限

- `networkTask.read` + `technician.readOwnNetwork`
- 任务必须属于上下文网点 ACTIVE NETWORK 责任
