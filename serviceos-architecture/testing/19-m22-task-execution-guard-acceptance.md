---
title: M22 TaskExecutionGuard 验收矩阵
version: 0.1.0
status: Proposed
owner: Fulfillment Platform QA
---

# M22 TaskExecutionGuard 验收矩阵

| ID | 优先级 | 场景 | 预期证据 |
|---|---|---|---|
| M22-GRD-001 | P0 | 非终态 HUMAN Task 获取 guard | Task version、ACTIVE guard、审计和 activated 事件原子提交 |
| M22-GRD-002 | P0 | 同一请求幂等重放 | 返回首次 guardId、版本和时间，不重复事件 |
| M22-GRD-003 | P0 | 同一 Task 并发获取第二个 guard | 409 `TASK_EXECUTION_GUARDED`，唯一 ACTIVE guard 不变 |
| M22-GRD-004 | P0 | ACTIVE guard 下 claim/start/complete/release | 409 `TASK_EXECUTION_GUARDED`，Task/Assignment 不变 |
| M22-GRD-005 | P0 | ACTIVE guard 下替换候选快照 | 409 `TASK_EXECUTION_GUARDED`，旧候选保持有效 |
| M22-GRD-006 | P0 | 精确 guard release | guard RELEASED、Task version 递增、released 事件原子提交 |
| M22-GRD-007 | P0 | 错误 guardId 或陈旧版本 release | 409，无任何解除或事件副作用 |
| M22-TX-001 | P0 | activated Outbox 写入失败 | Task version、guard、审计、幂等全部回滚 |
| M22-CONTRACT-001 | P0 | 两个事件 Schema 与样本 | 治理测试验证版本、eventType 与 JSON Schema |
| M22-MIG-001 | P0 | PostgreSQL 18 空库迁移 | current=`022`、applied=`24`、重复 migrate=0 |

Docker 不可用导致 PostgreSQL IT skipped 不计通过。
