---
title: M269 EXCLUSIVE_GATEWAY 运行时
status: Implemented
milestone: M269
lastUpdated: 2026-07-18
relatedMilestones: [M18, M19, M268]
---

# M269 EXCLUSIVE_GATEWAY 运行时

## 目标

在冻结 Workflow 版本上执行互斥网关：出边条件用 `SERVICEOS_EXPR_V1` 求值，恰好一条为 true 时推进；零命中/多命中失败关闭。

## 范围与非目标

- 范围：`WorkflowDefinitionParser` 网关解析、`WorkflowTaskCompletedHandler` 注入工单表达式上下文、PostgreSQL IT。
- 明确不做：PARALLEL_GATEWAY、WAIT_EVENT、默认边语义、网关 NodeInstance 落库审计增强、人工跳转。

## 已实现

- 任务完成后若唯一无条件出边指向 `EXCLUSIVE_GATEWAY`，按条件求值选择下一任务；
- 表达式上下文来自 `WorkOrderExpressionContextQuery`（工单冻结字段 + 当前 stage/taskType）；
- 零命中 / 多命中抛出失败关闭异常；
- 线性无条件推进回归保持。

## 工程证据

- `WorkflowDefinitionParserTest`
- `WorkflowExclusiveGatewayPostgresIT`
- `WorkflowLinearProgressionPostgresIT` / `WorkflowBootstrapPostgresIT` 回归
