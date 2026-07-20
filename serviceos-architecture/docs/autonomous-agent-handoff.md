---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- **进行中 Draft PR**：M362 整改列表/头级客户端能力预检
  （依赖 M356～M361：#183～#188）
- PR：https://github.com/hctwgl/serviceos/pull/189
- 分支：`cursor/m362-correction-header-capability-reject-6a78`
  （base：`cursor/m361-correction-capability-gate-6a78`）
- latestMilestone（本 PR）：**M362**
- Flyway：**131**；OpenAPI：**1.0.55**
- `baselineCommit`：合并后回填

## 负责人决策（已确认）

业务规则先都不动（AMOUNT/加权、BUSINESS SLA/结算、吉利联调均不推进）。
iOS 条件执行器在本 Linux/无 Xcode 环境为 **BLOCKED_EXTERNAL**，不得伪称 Implemented。
独立审核 HUMAN Task 模板分离与派单过滤均需 **Accepted 设计**后方可实施，不得发明推进。
Network Portal on-behalf 能力门禁需 Accepted 设计（`NETWORK_WEB` vs 代师傅端语义）。

## 下一

Technician 客户端能力门禁垂直切片（M356～M362）在本栈已闭环。剩余硬门禁项均需负责人确认后才能推进：

1. iOS 条件执行器对齐后全量硬阻断、派单过滤（需 Mac/Xcode + 派单规则确认）；
2. 独立审核 HUMAN Task 模板分离（需确认 taskId 绑定/触发/模板范围）；
3. Network Portal on-behalf 整改能力门禁（需确认代办 clientKind 语义）；
4. 硬门禁项未确认前不发明推进。
