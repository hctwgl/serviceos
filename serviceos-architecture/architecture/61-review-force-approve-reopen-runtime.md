---
title: M48 ReviewCase 强制通过与重开
version: 0.1.0
status: Implemented
---

# M48 ReviewCase 强制通过与重开

## 1. 实现范围

1. `POST /review-cases/{id}:force-approve`：对 **OPEN** ReviewCase 追加 `FORCE_APPROVED` 决定，投影状态 `FORCE_APPROVED`；
2. 强制通过需要能力 `evidence.forceApprove`，且必须提供 `reasonCodes`（未满足原条件）与 `approvalRef`；
3. 强制通过不伪装为普通 `APPROVED`，不删除既有机器校验或驳回事实，不打开 CorrectionCase；
4. `POST /review-cases/{id}:reopen`：对 **APPROVED|FORCE_APPROVED** 案例标记 `REOPENED`，并同事务创建同 Snapshot 的新 OPEN ReviewCase（201）；
5. 重开需要能力 `review.reopen`，必须提供 `reason` 与 `triggerRef`；同一 Snapshot 至多一个 OPEN 案例；
6. 旧决定与旧案例保留；新案例通过 `reopenedFromReviewCaseId` 追溯；
7. OpenAPI **0.23.0**；Flyway **V048**；staging **048/50**。

## 2. 未实现

车企回执、二级审批工作流、MFA、条件槽位、`WAIVED`、自动指派、OCR/CV。
