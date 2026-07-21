---
title: M390 Network 工作台与分配师傅产品化
status: Implemented
milestone: M390
lastUpdated: 2026-07-20
relatedMilestones: [M194, M196, M224, M256]
openapiVersion: "1.0.64"
flywayVersion: "138"
---

# M390 Network 工作台与分配师傅产品化

## 已实现

1. Network Web 经典专业协作风 Token 与白色 AppShell 基线；
2. 工作台 `SummaryStrip`（待分配 / 进行中 / SLA / 整改 / 异常 / 师傅）；
3. 待分配任务表：从服务端任务列表投影 `technicianId == null`；
4. `AssignTechnicianDrawer`：候选师傅、产能口径影响摘要、确认分配与处理中态；
5. Playwright + 1440 截图；产品状态 `READY_FOR_REVIEW`。

## 明确未实现

- 按师傅个人的今日已接、资质风险、距离、日程冲突、推荐解释正式读模型；
- 今日预约时间轴产品块；
- 工作区右侧分配抽屉联动；
- 人工视觉批准。
