---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-20
---

# ServiceOS 自主 Agent 交接

## 当前

- **状态**：**AWAITING_OWNER_SELECTION**（M366 已 Implemented；下一切片需选型）
- 已合并候选栈 tip：`cursor/m366-dispatch-client-kinds-filter-design-6a78`（Draft PR #193）
- 工程基线：**M366**（OpenAPI 1.0.58 / Flyway 134；ADR-088 A1-R～A5-R Accepted）

## 请选型下一切片（回复字母或改写）

| 选项 | 切片 | 说明 | 风险 |
|---|---|---|---|
| **A（推荐）** | Manual / Network assign **kind 硬拒绝**（ADR-088 **A1-B** 另案 → 建议 **M367**） | 在 M366 自动池过滤之上，对 Admin Manual / Network Portal assign/reassign 不兼容师傅 **422** + 拒绝审计；复用师傅声明与 Bundle 求交 | R2；语义已在 ADR-088 写明，需单独 Accept A1-B 切片边界 |
| B | Network Portal **on-behalf** 能力门禁 | 代补/整改路径按哪端 clientKind 预检 | **阻塞**：须确认 `NETWORK_WEB` vs 代师傅端语义 |
| C | iOS 条件执行器全量硬阻断 | 与 M356 目录对齐 | 本 Linux 环境多为 **`BLOCKED_EXTERNAL`** |
| D | 暂停 / 其他（请写明） | 吉利联调 / AMOUNT / BUSINESS SLA 仍为硬门禁 | 不可发明推进 |

### 若选 A，建议接受语句

```text
Accept M367 = ADR-088 A1-B: Manual + Network Portal assign/reassign hard-reject
incompatible technician supportedClientKinds (422 + deny audit); reuse M366
declaration + Bundle intersection; keep A5-R execution gates.
```

未选型前 **不得** 开 M367 实现或推进 `latestMilestone`。

## 已闭环

- M356～M363：Technician 客户端能力门禁 — #183～#190
- M364：独立审核 handling Task — #191
- M365：REVIEW_TASK 工作流门闸（A5-B）— #192
- M366：派单级 supportedClientKinds 过滤（A1-R～A5-R）— #193
