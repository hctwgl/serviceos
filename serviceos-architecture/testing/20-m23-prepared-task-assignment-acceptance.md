---
title: M23 PREPARED TaskAssignment 验收矩阵
version: 0.1.0
status: Proposed
owner: Fulfillment Platform QA
---

# M23 PREPARED TaskAssignment 验收矩阵

| ID | 优先级 | 场景 | 预期证据 |
|---|---|---|---|
| M23-PREP-001 | P0 | 已领取 Task prepare 新责任 | guard + PREPARED + Task version + 审计/事件原子提交 |
| M23-PREP-002 | P0 | prepare 重放或并发第二准备 | 首次响应冻结；第二准备 409，无半状态 |
| M23-PREP-003 | P0 | PREPARED 窗口执行人工命令 | 409 `TASK_EXECUTION_GUARDED` |
| M23-ACT-001 | P0 | 匹配 ServiceAssignment 激活 | 旧候选/责任 REVOKED，新候选/责任 ACTIVE，claimed_by 切换，guard RELEASED |
| M23-ACT-002 | P0 | ServiceAssignment ID/guard/assignment 不匹配 | 409，旧责任和 guard 保持不变 |
| M23-ACT-003 | P0 | 旧主体与新主体执行 | 旧主体被实时拒绝，新主体可继续 Task |
| M23-ABT-001 | P0 | 外部切换前 abort | PREPARED ABORTED、guard RELEASED，旧责任保持 ACTIVE |
| M23-TX-001 | P0 | activated Outbox 写入失败 | Task/Assignment/guard/审计/幂等全部回滚到可向前重试状态 |
| M23-CONTRACT-001 | P0 | 三个事件 Schema | 文件名、版本、eventType 与有效样本治理通过 |
| M23-MIG-001 | P0 | PostgreSQL 18 空库迁移 | current=`023`、applied=`25`、重复 migrate=0 |

Docker 不可用导致 PostgreSQL IT skipped 不计通过。
