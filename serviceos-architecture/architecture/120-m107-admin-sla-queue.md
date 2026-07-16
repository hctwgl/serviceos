---
title: M107 Admin SLA 工作台
status: Implemented
milestone: M107
---

# M107 Admin SLA 工作台

## 1. 目标

Admin 只读消费已实现的 `GET /sla-instances` 授权 SLA 工作台。

## 2. 交付

- 路由 `ADMIN.SLA.QUEUE`（`/sla`）；
- status 筛选（默认 BREACHED）；
- 深链工单工作区；`npm run build` 通过。

## 3. 明确未实现

SLA 详情/对账操作 UI、BUSINESS 日历、预警通知、E2E。
