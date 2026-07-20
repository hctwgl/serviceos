---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-20
---

# ServiceOS 自主 Agent 交接

## 当前

- **进行中 Draft PR**：M363 整改领取/启动能力硬拒单
  （依赖 M356～M362：#183～#189）
- PR：（创建后回填）
- 分支：`cursor/m363-correction-lifecycle-capability-hard-reject-6a78`
  （base：`cursor/m362-correction-header-capability-reject-6a78`）
- latestMilestone（本 PR）：**M363**
- Flyway：**131**；OpenAPI：**1.0.56**
- `baselineCommit`：合并后回填

## 负责人决策（已确认）

业务规则先都不动（AMOUNT/加权、BUSINESS SLA/结算、吉利联调均不推进）。
iOS 条件执行器在本 Linux/无 Xcode 环境为 **BLOCKED_EXTERNAL**，不得伪称 Implemented。
独立审核 HUMAN Task 模板分离与派单过滤均需 **Accepted 设计**后方可实施，不得发明推进。
Network Portal on-behalf 能力门禁需 Accepted 设计（`NETWORK_WEB` vs 代师傅端语义）。

## 已闭环

Technician 客户端能力门禁垂直切片 **M356～M363**：
发布门禁 → 运行时拒单 → 定向发布 → Feed/详情头 → 整改资料路径 → 整改列表预检 → 领取/启动硬拒。

## 下一（均需负责人确认）

1. iOS 条件执行器 + catalog 硬阻断（Mac/Xcode）；
2. 派单级 `supportedClientKinds` 过滤（Accepted 派单规则）；
3. Network Portal on-behalf 能力门禁（Accepted clientKind 语义）；
4. REVIEW_TASK 模板分离（Accepted 设计）；
5. 硬门禁项未确认前不发明推进。
