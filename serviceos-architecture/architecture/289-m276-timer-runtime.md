---
title: M276 TIMER 到期捕获运行时
status: Implemented
milestone: M276
lastUpdated: 2026-07-18
relatedMilestones: [M270, M275]
---

# M276 TIMER 到期捕获运行时

## 目标

支持 Workflow `TIMER` 节点：按 `durationSeconds` 挂起，到期由 worker claim/lease 唤醒并继续推进。

## 已实现

- schema：`TIMER` + `durationSeconds`
- Flyway V103：`wfl_timer_subscription`
- 推进时创建 WAITING 节点与定时订阅
- `JooqWorkflowTimerWorker` + `WorkflowTimerFireService` 幂等点火
- PostgreSQL IT

## 明确未实现

边界定时器、业务日历、超时转人工策略配置 UI、子流程。
