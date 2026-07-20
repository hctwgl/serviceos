---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-20
---

# ServiceOS 自主 Agent 交接

## 当前

- **状态**：**IMPLEMENTING / Draft PR**
- 进行中 Draft PR：M364 REVIEW_TASK 分离（Accepted ADR-087 → Implemented）
- PR：https://github.com/hctwgl/serviceos/pull/191
- 分支：`cursor/m364-review-task-separation-design-6a78`
  （base：`cursor/m363-correction-lifecycle-capability-hard-reject-6a78`）
- 工程基线目标：**M364**（OpenAPI 1.0.57 / Flyway 132）
- 决策包：`decisions/ADR-087-review-task-template-separation.md`（**Accepted**：A1-R～A5-R）

## 负责人已确认

```text
Accept ADR-087 with: A1-R, A2-R, A3-R, A4-R, A5-R
```

## 已闭环

- M356～M363（#183～#190）：Technician 客户端能力门禁垂直切片
- M364（#191）：独立审核 HUMAN Task / REVIEW_TASK 模板分离

## 下一候选（需选型；不得发明）

1. iOS 条件执行器 / 派单 `supportedClientKinds` 过滤
2. Network Portal on-behalf 能力门禁
3. A5-B 工作流节点推进（可选）
4. 吉利联调 / AMOUNT/加权 / BUSINESS 日历 SLA（硬门禁）
