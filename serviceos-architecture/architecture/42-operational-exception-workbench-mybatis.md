---
title: M29 运营异常工作台与 MyBatis 持久化基线
version: 0.1.0
status: Implemented
---

# M29 运营异常工作台与 MyBatis 持久化基线

## 1. 范围

M29 交付异常中心的首个可运行工作台切片：当前租户列表、详情、动态筛选、稳定游标，以及
`OPEN -> ACKNOWLEDGED` 人工确认。确认只表示运营人员已经接管，不代表来源领域恢复。

`IN_PROGRESS`、通用人工 `RESOLVED`、`CLOSED` 和 `SUPPRESSED` 暂不开放。解决异常必须调用真实
来源领域能力并附带恢复证据；在动作目录、审批口径和验证策略未确认前，页面不得伪造通用状态按钮。

## 2. 持久化边界

- Spring Boot 4 使用 MyBatis Starter 4；Mapper 仅存在于 `operations.infrastructure`。
- 应用服务只依赖 `OperationalExceptionWorkbenchRepository`，不得注入 Mapper 或 `JdbcClient`。
- 动态筛选、稳定排序与 keyset pagination 全部位于 MyBatis XML。
- 所有 SQL 显式携带 `tenant_id`，查询显式列名，排序固定为 `opened_at DESC, exception_id DESC`。
- 既有可靠消费服务的 Spring JDBC 暂不迁移，避免把 M29 扩成无关重写；后续修改时按持久化规范收敛。

## 3. 命令闭环

确认命令要求受信 `CurrentPrincipal`、`operations.exception.acknowledge`、`Idempotency-Key` 和双引号
`If-Match`。条件更新同时校验 tenant、OPEN 状态和聚合版本。成功后在同一事务写入：

1. `ACKNOWLEDGED` 与操作者/时间/备注；
2. 冻结命令响应；
3. `operational.exception.acknowledged@v1` Outbox；
4. 不可变审计；
5. 幂等完成记录。

同一幂等键同一请求返回首次冻结响应；变造请求拒绝。自动恢复允许从 OPEN 或 ACKNOWLEDGED 进入
RESOLVED，并保留人工确认事实。

## 4. 数据约束

V029 增加确认事实和 `aggregate_version`，数据库 CHECK 约束 OPEN、ACKNOWLEDGED、RESOLVED 三种
状态的证据组合。列表索引覆盖租户与稳定游标顺序；活动异常部分索引覆盖 OPEN/ACKNOWLEDGED。

## 5. 契约

HTTP API 提供 `/api/v1/operational-exceptions` 列表、详情和 `:acknowledge`。返回的 `allowedActions`
只包含服务端当前真正实现且状态允许的动作。事件发布 `operational.exception.acknowledged@v1`。
