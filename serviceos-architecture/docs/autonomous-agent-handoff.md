---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-20
---

# ServiceOS 自主 Agent 交接

## 当前

- **状态**：**AWAITING_ADR_ACCEPT**（A 已选型；实现未开工）
- 进行中 Draft PR：M366 派单级 `supportedClientKinds` 过滤 **设计提案**（非 Implemented）
- 分支：`cursor/m366-dispatch-client-kinds-filter-design-6a78`
  （base：`cursor/m365-review-task-workflow-gate-6a78`）
- 工程基线仍为 **M365**（OpenAPI 1.0.57 / Flyway 133）
- 决策包：`decisions/ADR-088-dispatch-supported-client-kinds-filter.md`（**Proposed**）

## 负责人已选型

- **A**：派单级 `supportedClientKinds` 过滤

## 阻塞实现的确认项（请一次性回复）

请确认或改写 ADR-088 推荐组合：

```text
Accept ADR-088 with: A1-R, A2-R, A3-R, A4-R, A5-R
```

| 点 | 推荐 | 一句话 |
|---|---|---|
| A1 | A1-R | 先只硬过滤自动 TECHNICIAN 池；Manual 可保留覆盖 |
| A2 | A2-R | 师傅声明 `supportedClientKinds` 为权威（非请求头） |
| A3 | A3-R | 自动池为空 → TECHNICIAN MANUAL + 原因码 |
| A4 | A4-R | FORM∩EVIDENCE 目标交集；全 null 不滤 kind |
| A5 | A5-R | 派单过滤 + 保留 M357～M363 执行门禁 |

未接受前 **不得** 改派单/师傅声明代码，也不得推进 `latestMilestone`→M366。

## 已闭环

- M356～M363：Technician 客户端能力门禁 — #183～#190
- M364：独立审核 handling Task — #191
- M365：REVIEW_TASK 工作流门闸（A5-B）— #192
