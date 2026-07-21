---
title: M425 Admin 工单工作区完整审核决策记录产品化
version: 0.1.0
status: Implemented
milestone: M425
lastUpdated: 2026-07-21
relatedMilestones: [M90, M389, M423]
openapiVersion: "1.0.90"
---

# M425 Admin 工单工作区完整审核决策记录产品化

## 1. 目标

关闭 M389/M423 `ADMIN.WORKORDER.WORKSPACE` UI_DATA_GAP 中「完整审核记录产品化」半部：将 `REVIEWS_CORRECTIONS` 区块已投影的 `reviews[].decisions[]` 与 `corrections[].resubmissions[]` 在 Admin 工作区「审核与整改」页签产品化展示。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **不变 1.0.90**（复用既有 `WorkOrderWorkspaceReviewDecisionSummary` / 补传摘要） |
| Backend | 无新端口；既有 `DefaultWorkOrderWorkspaceQueryService#loadReviewsCorrections` 已装配 decisions |
| Admin Web | 审核案例卡 + 决策表；整改卡 + 补传轮次表；保留详情深链 |
| 证据 | Playwright（页签切换 + 决策行断言 + 截图） |

## 3. 权限与边界

- 复用工作区 `REVIEWS_CORRECTIONS` 既有授权；缺权时区块仍失败关闭/不可用
- 不展示 `note` / `approvalRef` / `decidedBy`（工作区摘要刻意省略）
- 无 Flyway；无新 capability

## 4. 明确未实现

- 表单资料缩略图 / Evidence revision 预览（已由 **M426** 交付主路径）
- 决策备注与操作者显示名并入工作区摘要
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
