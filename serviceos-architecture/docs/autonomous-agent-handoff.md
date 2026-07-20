---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-20
---

# ServiceOS 自主 Agent 交接

## 当前

- **状态**：**READY_FOR_REVIEW**（M367 实现完成，待负责人选型下一切片）
- 进行中 Draft PR：M367 Manual/Network assign kind 硬拒绝（**Implemented**）
- 分支：`cursor/m367-manual-network-assign-kind-reject-6a78`
  （base：`cursor/m366-dispatch-client-kinds-filter-design-6a78`）
- 工程基线：**M367**（OpenAPI 1.0.58 / Flyway 134；ADR-088 A1-B 增补）

## 已接受并实现（本切片）

```text
Accept M367 = ADR-088 A1-B: Manual + Network Portal assign/reassign hard-reject
incompatible technician supportedClientKinds (422 + deny audit); reuse M366
declaration + Bundle intersection; keep A5-R execution gates.
```

## 下一切片候选（需负责人选型，不得发明）

1. Network Portal **on-behalf** 能力门禁（须确认 `NETWORK_WEB` vs 代师傅端）；
2. iOS 条件执行器全量硬阻断（本 Linux 环境多为 `BLOCKED_EXTERNAL`）；
3. 吉利联调 / AMOUNT/加权 / BUSINESS 日历 SLA（硬门禁）。

## 已闭环

- M356～M363：Technician 客户端能力门禁 — #183～#190
- M364：独立审核 handling Task — #191
- M365：REVIEW_TASK 工作流门闸（A5-B）— #192
- M366：派单级 supportedClientKinds 过滤（A1-R～A5-R）— #193
- M367：Manual/Network assign kind 硬拒绝（A1-B）— 本 PR
