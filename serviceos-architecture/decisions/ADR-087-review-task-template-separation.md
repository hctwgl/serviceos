---
title: ADR-087：独立审核 HUMAN Task（REVIEW_TASK）与提交 Task 分离
status: Proposed
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
  - product/05-cross-portal-interaction-state-spec.md
---

# ADR-087：独立审核 HUMAN Task（REVIEW_TASK）与提交 Task 分离

## 1. 状态

**Proposed（待负责人接受）**。在本 ADR 被标为 `Accepted` 且 A1～A5 全部勾选前，
**禁止**将 REVIEW_TASK 模板分离标记为 Implemented，也不得编写改变 ReviewCase/Task
绑定语义的生产代码。

背景：M353/M360～M363 已明确将「独立审核 HUMAN Task 与提交 Task 分离」列为未实现；
当前 `ReviewCase.taskId` 绑定 Snapshot 源提交 Task，`completeReviewTask` 对已 COMPLETED
提交 Task 只能跳过，无法形成可领取的审核责任面。

## 2. 问题

需要把「现场提交责任」与「客服审核责任」拆成两个 HUMAN Task，使：

1. 师傅提交 Task 可保持 COMPLETED（与 M266 整改旁路同构）；
2. 审核员领取/启动/完成的是独立 `REVIEW_TASK`；
3. 整改闭环后重新创建/激活审核任务，而不是篡改旧决定
   （`architecture/10` §12）。

## 3. 必须由负责人确认的决策点（A1～A5）

### A1 — `taskId` 绑定模型

| 选项 | 含义 |
|---|---|
| **A1-R（推荐）** | 保留 `ReviewCase.taskId` = **源提交 Task**（Snapshot 所属）；新增可空 `reviewTaskId` 指向独立 HUMAN `REVIEW_TASK` / `taskType` 待定。镜像 `CorrectionCase.taskId` + `correctionTaskId`。 |
| A1-B | 把 `ReviewCase.taskId` 语义改为审核 Task，另加 `sourceTaskId`（破坏性，需迁移与全站读模型改写）。 |

**推荐 A1-R**：不改既有 `taskId` 外键语义与目录索引；Final Review / 队列仍按源 Task 关联工单。

### A2 — 创建/激活触发

| 选项 | 含义 |
|---|---|
| **A2-R（推荐）** | **ReviewCase 驱动**：`create` 出 OPEN INTERNAL Case 时同事务创建并绑定 `reviewTaskId`（类比 M47 REJECTED→correction Task）。工作流模板可含 `REVIEW_TASK` 节点作编排对齐，但首切片不以“到达节点”为唯一触发。 |
| A2-B | **工作流驱动**：仅当流程推进到 `REVIEW_TASK` 节点时创建 Task；ReviewCase.create 只绑定已存在的 review Task。 |
| A2-C | 混合：首次由工作流创建；整改复审由 ReviewCase 创建。 |

**推荐 A2-R**：与 Correction 运行时对称，避免“有 Case 无 Task / 有 Task 无 Case”。

### A3 — 模板范围

| 选项 | 含义 |
|---|---|
| **A3-R（推荐）** | 首切片只改 **一个试点模板**（建议 `home-charging-survey-install`）：在资料提交 USER_TASK 之后增加 `REVIEW_TASK`；其余标准模板保持现状。`stageCode/taskType/assigneePolicyRef/slaRef` 使用显式试点常量，不发明全产品矩阵。 |
| A3-B | 全部 M281 标准模板同步加 `REVIEW_TASK`。 |
| A3-C | 不改种子模板，仅运行时按 ReviewCase 创建游离 HUMAN Task（无工作流节点）。 |

**推荐 A3-R**：可验证、可回滚；全量模板属后续配置里程碑。

### A4 — 整改后复审

| 选项 | 含义 |
|---|---|
| **A4-R（推荐）** | 对齐 `architecture/10` + product/05：**新 ReviewCase / 新 round**。权威 `CorrectionCase.CLOSED`（或现有“关闭后可建新 Case”入口）创建新 OPEN ReviewCase 并新建 `reviewTaskId`；旧 Case 决定只读。不把旧决定改为通过。 |
| A4-B | 同一 ReviewCase 上重新激活原 `reviewTaskId`（与“每轮独立决定”冲突，不推荐）。 |
| A4-C | `resubmit` 立即建新 ReviewCase（不等 CLOSED）。 |

**推荐 A4-R**：与“整改 Task 完成后重新创建/激活审核任务”一致；具体钩子优先复用现有 close 权威点。

### A5 — APPROVED 完成语义

| 选项 | 含义 |
|---|---|
| **A5-R（推荐）** | APPROVED 仅 `complete` **`reviewTaskId`**；永不试图 complete 源提交 Task（多已 COMPLETED）。删除/收紧当前对源 Task READY/CLAIMED/IN_PROGRESS 的跳过式 complete。 |
| A5-B | APPROVED 同时推进工作流离开 `REVIEW_TASK` 节点（若 A2-B/A3 使用工作流节点）。 |
| A5-C | APPROVED 不碰 Task，只写 ReviewDecision（保持现状弱绑定）。 |

**推荐 A5-R**；若接受 A3-R，可在同切片或紧随切片用工作流完成信号推进节点（A5-B 作可选增强，须单列验收）。

## 4. 明确非目标（接受后首切片仍不做）

- 审核人移动端 / 多候选人转派策略；
- CLIENT/车企 ReviewCase 的独立 REVIEW_TASK；
- 自动 Evidence target 映射、SLA enrich；
- 全量标准模板替换；
- 修复 `completeReviewTask` 对 `RUNNING` 误判（可作为同切片缺陷修复，但不替代 A1～A5）。

## 5. 建议实施切片（仅在 Accepted 后）

1. Flyway：`evd_review_case.review_task_id` + 唯一/外键（expand）；
2. `create` OPEN INTERNAL：同事务创建 HUMAN review Task 并写入 `reviewTaskId`；
3. 试点模板加入 `REVIEW_TASK`（A3-R）；
4. `decide(APPROVED)` 只完成 `reviewTaskId`（A5-R）；
5. 整改 CLOSED → 新 ReviewCase + 新 review Task（A4-R，可拆 M365）；
6. Final Review 投影优先展示 `reviewTaskId` 的 claim/start/complete；
7. OpenAPI / IT / ArchitectureTest / 试点 E2E。

## 6. 接受方式

负责人在本 ADR 或回复中明确：

```text
Accept ADR-087 with: A1-R, A2-R, A3-R, A4-R, A5-R
```

或给出替代组合。接受后将 `status` 改为 `Accepted`，再开实现里程碑（建议 M364）Draft PR。

## 7. 后果（接受后）

- ReviewCase 与 CorrectionCase 在「源 Task + 独立 handling Task」模型上对称；
- 消除 APPROVED 时对已完成提交 Task 的伪 complete；
- 工作流 `REVIEW_TASK` 从“仅 schema 存在”进入可运营试点模板。
