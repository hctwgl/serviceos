---
title: M28 ServiceAssignment 超时异常自动恢复验收矩阵
version: 0.1.0
status: Proposed
---

# M28 ServiceAssignment 超时异常自动恢复验收矩阵

| ID | Priority | 场景 | 预期证据 |
|---|---|---|---|
| M28-REC-001 | P0 | 超时异常后 saga 完成 | 异常 RESOLVED，未完成人工 Task CANCELLED，两个领域事件各一条 |
| M28-REC-002 | P0 | 同一完成事件重放 | 单一 Inbox、单一取消、单一解决结果和事件 |
| M28-REC-003 | P0 | 同 eventId 摘要变化 | `EVENT_PAYLOAD_MISMATCH`，既有结果不变 |
| M28-REC-004 | P0 | 人工处理 Task 已完成后 saga 恢复 | 保留 COMPLETED 人工事实，异常解决，只发异常解决事件 |
| M28-REC-005 | P0 | 未发生超时的 saga 正常完成 | 只完成恢复 Inbox，不创建异常、Task 或解决事件 |
| M28-TX-001 | P0 | Task 身份不符导致取消失败 | 异常仍 OPEN，恢复 Inbox/Outbox 全回滚 |
| M28-EVT-001 | P0 | 激活完成信封/载荷身份不一致 | 消费失败关闭，不解决异常 |
| M28-DB-001 | P0 | CANCELLED/RESOLVED 缺恢复证据 | PostgreSQL CHECK 拒绝写入 |
| M28-MIG-001 | P0 | PostgreSQL 18 空库迁移 | current=`028`、applied=`30`、重复 migrate=0 |
| M28-CON-001 | P0 | 新事件契约治理 | `task.cancelled@v1` 与 `operational.exception.resolved@v1` 样例通过且历史 schema 不变 |
