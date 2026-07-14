---
title: M30 预约修订与并发控制验收矩阵
version: 0.1.0
status: Implemented
---

# M30 预约修订与并发控制验收矩阵

| ID | Priority | 场景 | 预期证据 |
|---|---|---|---|
| M30-APT-001 | P0 | 勘察与安装分别提议、确认 | 两个聚合拥有独立、连续的不可变修订链 |
| M30-APT-002 | P0 | 已确认预约改约 | 追加 PROPOSED 修订，旧确认事实保留且新修订必须重新确认 |
| M30-CON-001 | P0 | 两个操作者基于同一版本并发改约 | 仅一个条件更新成功，失败方返回 `APPOINTMENT_VERSION_CONFLICT` |
| M30-IDM-001 | P0 | 相同幂等键重放/变造 | 同请求返回首次冻结响应，不同请求 `IDEMPOTENCY_KEY_REUSED` |
| M30-SEC-001 | P0 | project/network/tenant 实时授权 | 请求携带实时 scope，跨租户按不存在处理，无 grant 拒绝 |
| M30-RSP-001 | P0 | Task 与 Dispatch 技师责任不一致 | `SERVICE_ASSIGNMENT_CONFLICT`，不创建预约 |
| M30-TX-001 | P0 | 命令提交成功 | 状态、修订、历史、冻结响应、审计、Outbox、幂等同事务提交 |
| M30-DB-001 | P0 | 非法时间窗或确认事实组合 | PostgreSQL CHECK 拒绝非法数据 |
| M30-MIG-001 | P0 | PostgreSQL 18 空库迁移 | current=`030`、applied=`32`、重复 migrate=0 |
| M30-CONTRACT-001 | P0 | HTTP/事件契约 | OpenAPI 0.6.0 与三个 appointment v1 事件治理通过 |
