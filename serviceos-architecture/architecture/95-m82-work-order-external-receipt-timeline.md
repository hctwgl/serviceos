---
title: M82 工单外部审核回执时间线事件合并
status: Implemented
milestone: M82
---

# M82 工单外部审核回执时间线事件合并

## 1. 目标

为 ReviewCase 增加公开最小查询端口 `ReviewTimelineContextQuery`，并在既有工单时间线投影上
合并 `evidence.external-review-receipt-recorded@v1`（补齐 M76 因缺少直接 taskId 而延后的回执可见性）。

不实现 evidence revision/slots 技术噪声、DATA-06 checkpoint/重建、试算/结算或 Portal。

## 2. 模块边界

- `evidence::api` 暴露 `ReviewTimelineContext(reviewCaseId, projectId, workOrderId)`；
- 查询只读 ReviewCase 三列身份，再经 `TaskTimelineContextQuery` 解析工单；不加载决定图；
- `readmodel` 通过 `allowedDependencies` 依赖 `evidence::api`；
- 载荷 `projectId` 必须与权威 ReviewCase 一致；缺少 Case 失败关闭；
- 不投影 externalKey、envelope、canonical、coordinationTaskId。

## 3. 映射

| 事件 | outcome | actor |
|---|---|---|
| external-review-receipt-recorded | `result`（APPROVED/REJECTED） | `receivedBy` |

category `REVIEW`，resourceType `ExternalReviewReceipt`。

## 4. 契约

Core OpenAPI 0.53.0 扩展 timeline `x-extensible-enum`。无新 Flyway（沿用 V074 REVIEW）。

## 5. 明确未实现

revision/slots 噪声、checkpoint/重建、试算/结算、Portal。
