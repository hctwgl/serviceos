---
title: M364 独立审核 REVIEW_TASK 模板分离
status: Implemented
milestone: M364
lastUpdated: 2026-07-20
relatedMilestones: [M353, M266, M47, M20, M281, M363]
openapiVersion: "1.0.57"
flywayVersion: "132"
---

# M364 独立审核 REVIEW_TASK 模板分离

## 状态

**Implemented**。依据已接受
[`ADR-087`](../decisions/ADR-087-review-task-template-separation.md)
（A1-R / A2-R / A3-R / A4-R / A5-R）。

## 目标

将客服审核责任从源提交 Task 剥离到独立 HUMAN handling Task（`reviewTaskId`），使
领取/完成与 Final Review 动作面建立在可操作的审核 Task 上。

## 已实现范围

1. Flyway V132：`evd_review_case.review_task_id` + 唯一部分索引；守卫禁止改绑；
2. `create` / `reopen` OPEN INTERNAL：同事务 `createHandlingTask(taskType=evidence.review)` 并写入 `reviewTaskId`；
3. `decide` / `forceApprove`：仅 `completeHandlingTask(reviewTaskId)`，不碰源 `taskId`；
4. `CorrectionCase.close`：CLOSED 后经 `ReviewCaseHandlingBootstrap` 为补传 Snapshot 打开新 Case+Task；
5. Final Review 投影：动作面 `taskId` 优先 `reviewTaskId`；证据扇入仍用源提交 Task；
6. 试点模板 `home-charging-survey-install` 增加 `REVIEW_TASK` 节点（编排对齐；主路径仍 INSTALL→WAIT_OEM，避免与 A2-R 双触发）；
7. OpenAPI 1.0.57：`ReviewCase` / `ReviewCaseQueueItem.reviewTaskId`。

## 明确未实现

- CLIENT Case 的 `reviewTaskId`；
- 工作流到达 `REVIEW_TASK` 节点才创建 Task（A2-B）及 APPROVED 推进节点（A5-B）；
- 全量标准模板、审核移动端、多候选人策略。

## 验证

```bash
bash scripts/agent-verify.sh it ReviewCasePostgresIT,CorrectionCasePostgresIT
bash scripts/agent-verify.sh test ReviewCaseControllerSecurityTest,ReviewCaseQueryControllerSecurityTest
bash scripts/agent-verify.sh arch
bash scripts/agent-verify.sh contracts
bash scripts/agent-verify.sh client-ts
bash scripts/agent-verify.sh docs
```
