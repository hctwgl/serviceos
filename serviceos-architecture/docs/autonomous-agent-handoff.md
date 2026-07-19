---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- **进行中 Draft PR**：M361 整改路径客户端能力门禁对齐
  （依赖 M356～M360：#183～#187）
- PR：https://github.com/hctwgl/serviceos/pull/188
- 分支：`cursor/m361-correction-capability-gate-6a78`
  （base：`cursor/m360-final-review-visual-admin-pilot-6a78`）
- latestMilestone（本 PR）：**M361**
- Flyway：**131**；OpenAPI：**1.0.54**
- `baselineCommit`：合并后回填

## 负责人决策（已确认）

业务规则先都不动（AMOUNT/加权、BUSINESS SLA/结算、吉利联调均不推进）。
iOS 条件执行器在本 Linux/无 Xcode 环境为 **BLOCKED_EXTERNAL**，不得伪称 Implemented。
独立审核 HUMAN Task 模板分离与派单过滤均需 **Accepted 设计**后方可实施，不得发明推进。

## 下一

1. iOS 条件执行器对齐后全量硬阻断、派单过滤（需 Mac/Xcode + 派单规则确认）；
2. 独立审核 HUMAN Task 模板分离（需确认 taskId 绑定/触发/模板范围）；
3. 硬门禁项未确认前不发明推进。
