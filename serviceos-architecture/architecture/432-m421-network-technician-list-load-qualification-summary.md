---
title: M421 Network 师傅列表资质与开放任务摘要
version: 0.1.0
status: Implemented
milestone: M421
lastUpdated: 2026-07-21
relatedMilestones: [M194, M396, M407]
openapiVersion: "1.0.87"
---

# M421 Network 师傅列表资质与开放任务摘要

## 1. 目标

关闭 M396 `NETWORK.TECHNICIAN.LIST` UI_DATA_GAP 中可由本网点事实证明的部分：当前开放任务量与资质计数/摘要，口径对齐 M407 分配候选，避免列表与分配抽屉数字分裂。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.87** `NetworkPortalTechnicianItem` 增加 `openTaskCount` / `approvedQualificationCount` / `pendingQualificationCount` / `qualificationSummary`（required） |
| ReadModel | `listTechnicians` 与工作区/目录 technician fan-in 共用计数辅助方法；开放任务=本网点 ACTIVE NETWORK 责任已指派数；资质状态口径同 M407 |
| Network Web | 师傅列表表格列 + SummaryStrip；诚实更新 UI_DATA_GAP 文案 |
| 证据 | `NetworkPortalReadPostgresIT` + ArchitectureTest + Playwright |

## 3. 权限

- 硬门禁不变：`technician.readOwnNetwork` + `X-Network-Context`
- 无新增 capability；计数不要求额外 soft-gate

## 4. 明确未实现

- 技能 taxonomy / 服务区域 / 最近同步 / 资质到期提醒专用读模型
- 经纬度路网距离、数值推荐评分
- 产能申请写流程
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
