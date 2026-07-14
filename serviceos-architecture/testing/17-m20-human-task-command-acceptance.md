---
title: M20 人工工作流 Task 命令验收矩阵
version: 0.1.0
status: Proposed
owner: Fulfillment Platform QA
---

# M20 人工工作流 Task 命令验收矩阵

| ID | 优先级 | 场景 | 预期证据 |
|---|---|---|---|
| M20-TASK-001 | P0 | HUMAN READY 执行 claim→start→complete | CLAIMED→RUNNING→COMPLETED，版本 1→4 |
| M20-TASK-002 | P0 | 相同幂等键在后续状态推进后重放 | 返回首次冻结 receipt，不重复审计/事件 |
| M20-TASK-003 | P0 | 同幂等键不同请求摘要 | 409 `IDEMPOTENCY_KEY_REUSED`，无额外写入 |
| M20-TASK-004 | P0 | 陈旧 If-Match 或并发领取 | 409 `VERSION_CONFLICT`，仅一人领取成功 |
| M20-TASK-005 | P0 | 非当前领取人 start/complete | 409 `TASK_STATE_CONFLICT`，状态不变 |
| M20-TX-001 | P0 | `task.completed` Outbox 写入注入失败 | Task、审计、幂等、冻结响应全部回滚 |
| M20-FLOW-001 | P0 | USER_TASK 完成并发布 TaskCompleted | Node/Stage/Workflow 完成且 WorkOrder FULFILLED |
| M20-SEC-001 | P0 | 未认证或伪造 actor/tenant 头 | 401；命令只使用 JWT 映射主体 |
| M20-CONTRACT-001 | P0 | OpenAPI 与三类事件 | 可解析、样本过 Schema、TS 客户端可复现 |
| M20-MIG-001 | P0 | PostgreSQL 18 空库迁移 | current=`020`、applied=`22`、重复 migrate=0 |
| M20-DEP-001 | P0 | staging 发布 | migration Gate 验证 `020/22` |

Docker 不可用导致 PostgreSQL IT skipped 不计通过。
