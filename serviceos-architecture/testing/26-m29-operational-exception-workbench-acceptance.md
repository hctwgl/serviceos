---
title: M29 运营异常工作台验收矩阵
version: 0.1.0
status: Implemented
---

# M29 运营异常工作台验收矩阵

| ID | Priority | 场景 | 预期证据 |
|---|---|---|---|
| M29-QRY-001 | P0 | 同租户列表/详情 | 只返回 JWT tenant 数据，跨租户按不存在处理 |
| M29-QRY-002 | P0 | 状态、分类、严重度、工单、Task 动态筛选 | MyBatis XML 组合条件正确且始终含 tenant |
| M29-QRY-003 | P0 | 相同 openedAt 的稳定分页 | 以 exceptionId 破同值，无重复、无遗漏 |
| M29-CMD-001 | P0 | OPEN 异常确认 | 状态 ACKNOWLEDGED、版本 +1、确认人和时间冻结 |
| M29-CMD-002 | P0 | 同幂等键重放/变造 | 同请求返回首次响应，不同请求 `IDEMPOTENCY_KEY_REUSED` |
| M29-CMD-003 | P0 | 陈旧版本或重复确认 | 条件更新失败并返回 `VERSION_CONFLICT` |
| M29-SEC-001 | P0 | 读/确认能力校验 | RoleGrant 实时授权，token 能力声明不能越权 |
| M29-TX-001 | P0 | 确认成功 | 状态、冻结响应、审计、Outbox、幂等记录同事务提交 |
| M29-REC-001 | P0 | ACKNOWLEDGED 后来源自动恢复 | 允许进入 RESOLVED，并保留确认事实 |
| M29-DB-001 | P0 | 状态缺少对应证据 | PostgreSQL CHECK 拒绝非法组合 |
| M29-MIG-001 | P0 | PostgreSQL 18 空库迁移 | current=`029`、applied=`31`、重复 migrate=0 |
| M29-CON-001 | P0 | HTTP/事件契约 | OpenAPI 0.5.0 与 `operational.exception.acknowledged@v1` 治理通过 |
