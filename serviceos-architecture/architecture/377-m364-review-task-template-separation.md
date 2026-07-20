---
title: M364 独立审核 REVIEW_TASK 模板分离（设计提案）
status: Proposed
milestone: M364
lastUpdated: 2026-07-20
relatedMilestones: [M353, M266, M47, M20, M281]
openapiVersion: "1.0.56"
flywayVersion: "131"
---

# M364 独立审核 REVIEW_TASK 模板分离（设计提案）

## 状态

**Proposed**。本文件不是 Implemented 里程碑；在
[`ADR-087`](../decisions/ADR-087-review-task-template-separation.md) 被接受
（A1～A5 勾选）前，不得编写绑定语义代码，也不得把 `latestMilestone` 推进到 M364。

## 目标（接受后）

将客服审核责任从源提交 Task 剥离到独立 HUMAN `REVIEW_TASK`，使领取/启动/完成与
Final Review 动作面建立在可操作的审核 Task 上。

## 当前缺口（工程事实）

- `ReviewCase.taskId` = Snapshot 源提交 Task；
- `completeReviewTask` 对已 COMPLETED 源 Task 跳过；
- 标准模板种子无 `REVIEW_TASK` 节点；
- M353 明确未实现模板分离。

## 设计入口

权威决策包：`decisions/ADR-087-review-task-template-separation.md`（Proposed）。

推荐默认（待接受）：A1-R / A2-R / A3-R / A4-R / A5-R。

## 接受后的实现边界（预告，非承诺）

- 范围：`reviewTaskId` 列、create 绑 Task、试点模板、APPROVED complete review Task、
  Final Review 投影对齐；
- 非目标：全模板、CLIENT Case、移动审核端、多候选人策略。

## 验证（仅 Accepted 实现阶段）

```bash
bash scripts/agent-verify.sh it ReviewCasePostgresIT,CorrectionCasePostgresIT
bash scripts/agent-verify.sh arch
bash scripts/agent-verify.sh contracts
```
