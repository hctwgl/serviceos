---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-20
---

# ServiceOS 自主 Agent 交接

## 当前

- **状态**：**READY_FOR_REVIEW**（M366 实现完成，待负责人选型下一切片）
- 进行中 Draft PR：M366 派单级 `supportedClientKinds` 过滤（**Implemented**）
- 分支：`cursor/m366-dispatch-client-kinds-filter-design-6a78`
  （base：`cursor/m365-review-task-workflow-gate-6a78`）
- 工程基线：**M366**（OpenAPI 1.0.58 / Flyway 134）
- 决策包：`decisions/ADR-088-dispatch-supported-client-kinds-filter.md`（**Accepted**：A1-R～A5-R）

## 已接受并实现

```text
Accept ADR-088 with: A1-R, A2-R, A3-R, A4-R, A5-R
```

| 点 | 选择 | 实现要点 |
|---|---|---|
| A1-R | 仅自动 TECHNICIAN 池硬过滤 | `activateTechnician` |
| A2-R | 师傅声明权威 | V134 + create/declare API |
| A3-R | 空池 → MANUAL + `CLIENT_KIND_TARGET_EMPTY` | audit `error_code` |
| A4-R | FORM∩EVIDENCE 交集 | `resolveDispatchTargetClientKinds` |
| A5-R | 保留执行门禁 | 未改 M357～M363 |

## 下一切片候选（需负责人选型，不得发明）

1. Manual / Network assign 硬拒绝（A1-B，另案）；
2. Network Portal on-behalf 整改/代补能力门禁（需确认 `NETWORK_WEB` vs 代师傅端）；
3. iOS 条件执行器全量硬阻断（本 Linux 环境多为 `BLOCKED_EXTERNAL`）；
4. 吉利联调 / AMOUNT/加权 / BUSINESS 日历 SLA（硬门禁）。

## 已闭环

- M356～M363：Technician 客户端能力门禁 — #183～#190
- M364：独立审核 handling Task — #191
- M365：REVIEW_TASK 工作流门闸（A5-B）— #192
- M366：派单级 supportedClientKinds 过滤 — 本 PR
