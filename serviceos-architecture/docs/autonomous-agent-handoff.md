---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-20
---

# ServiceOS 自主 Agent 交接

## 当前

- **状态**：**PAUSED_AWAITING_OWNER**（无未阻塞可实施切片）
- 最近完成 Draft PR 栈：M356～M362（#183～#189）
- 栈尖：https://github.com/hctwgl/serviceos/pull/189
  （`cursor/m362-correction-header-capability-reject-6a78`）
- latestMilestone（栈尖）：**M362**
- Flyway：**131**；OpenAPI：**1.0.55**
- `baselineCommit`：合并后回填

## 负责人决策（已确认）

业务规则先都不动（AMOUNT/加权、BUSINESS SLA/结算、吉利联调均不推进）。
iOS 条件执行器在本 Linux/无 Xcode 环境为 **BLOCKED_EXTERNAL**，不得伪称 Implemented。
独立审核 HUMAN Task 模板分离与派单过滤均需 **Accepted 设计**后方可实施，不得发明推进。
Network Portal on-behalf 能力门禁需 Accepted 设计（`NETWORK_WEB` vs 代师傅端语义）。

## 已闭环

Technician 客户端能力门禁垂直切片 **M356～M362**：
发布门禁 → 运行时拒单 → 定向发布 → Feed/详情头 → 整改资料路径 → 整改列表头级预检。

## 下一（请负责人三选一或另指方向）

| 选项 | 依赖 | 说明 |
|---|---|---|
| A. iOS 条件执行器 + catalog 硬阻断翻转 | Mac/Xcode | Linux 无法伪实现 |
| B. 派单级 `supportedClientKinds` 过滤 | Accepted 派单亲和规则 | 不得自创过滤语义 |
| C. Network Portal on-behalf 能力门禁 | Accepted：`NETWORK_WEB` vs 代师傅 clientKind | 不得自创代办能力模型 |
| D. REVIEW_TASK 模板分离 | Accepted：taskId 绑定/触发/模板范围 | R3 |
| E. 其他方向 | 负责人明示 | 例如文档收口/合并栈/别的模块 |

硬门禁项未确认前 **不发明推进**。
