---
title: M96 工单工作区审核血缘元数据
status: Implemented
milestone: M96
---

# M96 工单工作区审核血缘元数据

## 1. 目标

扩展 `REVIEWS_CORRECTIONS` 的 ReviewCase 摘要，展示既有 INTERNAL→CLIENT 与重开血缘。

## 2. 接受范围

- CLIENT：`sourceReviewCaseId`、`externalSubmissionRef`、`callbackBatchRef`、
  `mappingVersionId`；
- 重开：`reopenedFromReviewCaseId`、`reopenTriggerRef`；
- 复用 M90 `ReviewCaseService.listForTask`、`evidence.read` 与 Project Scope；
- 不新增端口、数据库、事务或状态机语义。

## 3. 安全边界

仍不返回 ReviewCase createdBy/snapshotContentDigest、Decision note/approvalRef/decidedBy、
Correction waiveNote/approvalRef/操作者及任何回调正文。

## 4. 契约

Core OpenAPI **0.66.0**。无新 Flyway，保持 V080 / 82 migrations。

## 5. 明确未实现

回调批次到多工单的额外归属、审核队列、命令聚合、FACTS_CALCULATIONS、
customer/location、SavedView、Portal。
