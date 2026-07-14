---
title: M21 TaskAssignment 候选与责任验收矩阵
version: 0.1.0
status: Proposed
owner: Fulfillment Platform QA
---

# M21 TaskAssignment 候选与责任验收矩阵

| ID | 优先级 | 场景 | 预期证据 |
|---|---|---|---|
| M21-ASN-001 | P0 | READY HUMAN 冻结候选快照 | 批次、候选、Task version、审计和 TaskAssigned 原子提交 |
| M21-ASN-002 | P0 | 非候选人具备 task.claim capability | 409 `TASK_ASSIGNMENT_CONFLICT`，Task 不变 |
| M21-ASN-003 | P0 | 两名候选领取同一 Task | 乐观锁与唯一 ACTIVE RESPONSIBLE 只允许一人成功 |
| M21-ASN-004 | P0 | 替换候选快照 | 旧候选 REVOKED、新候选 ACTIVE、历史批次保留 |
| M21-ASN-005 | P0 | 相同幂等键重放/变更 | 首次响应冻结；请求变化 409，不重复 Assignment/Event |
| M21-REL-001 | P0 | 当前责任人 start 前 release | RESPONSIBLE REVOKED、Task READY、原候选可重领 |
| M21-REL-002 | P0 | 非责任人或 RUNNING Task release | 409，状态与责任不变 |
| M21-END-001 | P0 | 责任人完成 Task | ACTIVE 候选和责任全部 EXPIRED，工作流照常推进 |
| M21-TX-001 | P0 | TaskAssigned Outbox 写入失败 | Task version、候选批次、审计、幂等全部回滚 |
| M21-SEC-001 | P0 | 未认证/伪造主体头 | 401；操作者只取 JWT 映射主体 |
| M21-CONTRACT-001 | P0 | OpenAPI、事件 Schema、客户端 | 可解析、样本通过、生成结果可复现 |
| M21-MIG-001 | P0 | PostgreSQL 18 空库迁移 | current=`021`、applied=`23`、重复 migrate=0 |

Docker 不可用导致 PostgreSQL IT skipped 不计通过。
