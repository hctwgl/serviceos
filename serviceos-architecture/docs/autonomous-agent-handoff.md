---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-20
---

# ServiceOS 自主 Agent 交接

## 当前

- **状态**：**AWAITING_ADR_ACCEPT**（D 已选型；实现未开工）
- 进行中 Draft PR：M364 REVIEW_TASK 分离 **设计提案**（非 Implemented）
- PR：（创建后回填）
- 分支：`cursor/m364-review-task-separation-design-6a78`
  （base：`cursor/m363-correction-lifecycle-capability-hard-reject-6a78`）
- 工程基线仍为 **M363**（OpenAPI 1.0.56 / Flyway 131）
- 决策包：`decisions/ADR-087-review-task-template-separation.md`（**Proposed**）

## 负责人已选型

- **D**：独立审核 HUMAN Task / REVIEW_TASK 模板分离

## 阻塞实现的确认项（请一次性回复）

请确认或改写 ADR-087 推荐组合：

```text
Accept ADR-087 with: A1-R, A2-R, A3-R, A4-R, A5-R
```

| 点 | 推荐 | 一句话 |
|---|---|---|
| A1 | A1-R | 保留 `taskId`=源提交 Task；新增 `reviewTaskId` |
| A2 | A2-R | ReviewCase.create 同事务创建审核 Task（类比 Correction） |
| A3 | A3-R | 仅试点模板 `home-charging-survey-install` 加 REVIEW_TASK |
| A4 | A4-R | 整改 CLOSED 后新 ReviewCase + 新 review Task |
| A5 | A5-R | APPROVED 只 complete `reviewTaskId` |

未接受前 **不得** 改 ReviewCase/Task 绑定代码，也不得推进 `latestMilestone`→M364。

## 已闭环（能力门禁）

M356～M363（#183～#190）：Technician 客户端能力门禁垂直切片。
