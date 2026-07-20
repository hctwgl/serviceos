---
title: ADR-087：独立审核 HUMAN Task（REVIEW_TASK）与提交 Task 分离
status: Accepted
date: 2026-07-20
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Evidence Owner
  - Workflow Owner
related_adrs:
  - decisions/ADR-017-workflow-runtime-selection.md
related_docs:
  - architecture/10-evidence-review-correction.md
  - architecture/60-correction-task-runtime.md
  - architecture/279-m266-technician-correction-batch.md
  - architecture/366-m353-review-target-decide.md
  - architecture/377-m364-review-task-template-separation.md
  - product/05-cross-portal-interaction-state-spec.md
---

# ADR-087：独立审核 HUMAN Task（REVIEW_TASK）与提交 Task 分离

## 1. 状态

**Accepted**（2026-07-20）。负责人确认推荐组合：

```text
Accept ADR-087 with: A1-R, A2-R, A3-R, A4-R, A5-R
```

实现里程碑：M364（`architecture/377-m364-review-task-template-separation.md`）。

背景：M353/M360～M363 已明确将「独立审核 HUMAN Task 与提交 Task 分离」列为未实现；
历史实现将 `ReviewCase.taskId` 绑定 Snapshot 源提交 Task，`completeReviewTask` 对已 COMPLETED
提交 Task 只能跳过，无法形成可领取的审核责任面。

## 2. 问题

需要把「现场提交责任」与「客服审核责任」拆成两个 HUMAN Task，使：

1. 师傅提交 Task 可保持 COMPLETED（与 M266 整改旁路同构）；
2. 审核员领取/启动/完成的是独立 `REVIEW_TASK`；
3. 整改闭环后重新创建/激活审核任务，而不是篡改旧决定
   （`architecture/10` §12）。

## 3. 已接受决策（A1-R～A5-R）

| ID | 决策 |
|---|---|
| **A1-R** | 保留 `ReviewCase.taskId` = 源提交 Task；新增可空 `reviewTaskId` 指向独立 HUMAN handling Task（`taskType=evidence.review`）。 |
| **A2-R** | ReviewCase 驱动：`create` / `reopen` / 整改复审打开 OPEN INTERNAL Case 时同事务创建并绑定 `reviewTaskId`。 |
| **A3-R** | 首切片只改试点模板 `home-charging-survey-install`：增加 `REVIEW_TASK` 节点作编排对齐；运行时创建仍由 ReviewCase 驱动，主路径不改为“到达节点才建 Task”。 |
| **A4-R** | `CorrectionCase.CLOSED` 后为最新补传 Snapshot 打开**新** INTERNAL ReviewCase + **新** `reviewTaskId`；旧决定只读。 |
| **A5-R** | `decide`（APPROVED/REJECTED）与 `forceApprove` 仅 `completeHandlingTask` **`reviewTaskId`**；永不 complete 源提交 Task。 |

## 4. 明确非目标（M364 仍不做）

- 审核人移动端 / 多候选人转派策略；
- CLIENT/车企 ReviewCase 的独立 REVIEW_TASK；
- 自动 Evidence target 映射、SLA enrich；
- 全量标准模板替换；
- APPROVED 后推进工作流离开 `REVIEW_TASK` 节点（A5-B，可后续单列）。

## 5. 后果

- ReviewCase 与 CorrectionCase 在「源 Task + 独立 handling Task」模型上对称；
- 消除 APPROVED 时对已完成提交 Task 的伪 complete；
- 试点模板具备 `REVIEW_TASK` 节点声明；运行时绑定由 ReviewCase 权威创建。
