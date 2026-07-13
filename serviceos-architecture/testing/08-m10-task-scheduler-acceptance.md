---
title: M10 Task Scheduler 与人工接管验收矩阵
version: 0.1.0
status: Proposed
---

# M10 Task Scheduler 与人工接管验收矩阵

| ID | Priority | 场景 | 预期证据 | 自动化层次 |
|---|---|---|---|---|
| M10-TASK-001 | P0 | 同业务键/同摘要重复调度 | 返回同一 Task | PostgreSQL IT |
| M10-TASK-002 | P0 | 同业务键/不同摘要 | TASK_SCHEDULE_CONFLICT，零额外写入 | PostgreSQL IT |
| M10-TASK-003 | P0 | 两 worker 竞争 | 活跃租约仅一个拥有者 | PostgreSQL IT |
| M10-TASK-004 | P0 | 租约过期重认领 | 旧 Attempt=LEASE_EXPIRED，新 attemptNo+1 | PostgreSQL IT |
| M10-TASK-005 | P0 | 旧 worker 晚到结果 | TASK_LEASE_LOST，不覆盖新结果 | PostgreSQL IT |
| M10-TASK-006 | P0 | 明确成功 | Task/Attempt/Outbox 原子成功 | Unit + PostgreSQL IT |
| M10-TASK-007 | P0 | 明确可重试 | 只使用 handler 给出的 nextRunAt | Unit |
| M10-TASK-008 | P0 | 外部结果 UNKNOWN | 不重试，转 MANUAL_INTERVENTION | Unit |
| M10-TASK-009 | P0 | 未分类异常/缺 handler | fail closed，转人工 | Unit |
| M10-TASK-010 | P0 | 最大尝试耗尽 | MANUAL_INTERVENTION + 失败事件 | PostgreSQL IT |
| M10-TASK-011 | P0 | 最后一次执行崩溃 | 租约到期后转人工，不产生额外执行 | Unit + PostgreSQL IT |
| M10-OPS-001 | P0 | 最终失败事件重复 | 一个 OperationalException + 一个 HUMAN Task | PostgreSQL IT |
| M10-OPS-002 | P0 | 同 eventId 载荷变化 | EVENT_PAYLOAD_MISMATCH | PostgreSQL IT |
| M10-CON-001 | P0 | 人工接管事件契约 | 示例通过 JSON Schema 2020-12 | Contract test |

本机没有容器运行时时 PostgreSQL IT 明确跳过；CI 必须先通过容器运行时门禁再运行 `clean verify`。正式 Broker、authority fence 和通用人工 Task 动作不属于本矩阵的已完成证据。
