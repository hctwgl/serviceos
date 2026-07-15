---
title: M72 Task 执行 Attempt 历史查询
status: Implemented
milestone: M72
---

# M72 Task 执行 Attempt 历史查询

## 1. 目标

实现 `GET /api/v1/tasks/{taskId}/execution-attempts`，把自动 Task 已由 V008 持久化的执行 Attempt
安全暴露给任务详情、运营排障和后续 Portal。M72 只建立授权只读投影，不改变 Task 执行、重试、
租约恢复或人工接管语义。

## 2. 权威与授权边界

- 每次查询先复用 M70 Task 详情读取，完成 tenant 隔离和实时 `task.read` 项目/租户鉴权；
- 只读取 Task 模块自己的 `tsk_task_execution_attempt`，不跨模块读取 Delivery、异常或业务结果表；
- Attempt 按 `attemptNo DESC` 稳定分页，cursor 必须绑定 taskId，不能跨 Task 复用；
- HUMAN Task 当前没有执行 Attempt，明确返回空页，不伪造人工命令历史；
- 返回 Attempt 标识、序号、结果码、安全错误码、结果引用、重试时间和起止时间，不返回 workerId、
  payload、错误正文、私有响应或凭据；
- RUNNING Attempt 允许随后被现有 Worker 更新为终态；`asOf` 表示本页读取时间，调用方不得把查询
  快照当作新的执行事实源。

## 3. 契约与分页

响应包含 Task `resourceVersion`、Attempt 列表、下一页 cursor 和服务端 `asOf`。单页 1～100 条，默认
50 条；已有 `(task_id, attempt_no DESC)` 索引支撑查询，因此 M72 不新增迁移，Flyway 保持 V070/72。

## 4. 可靠性

- 查询不修改 Task、Attempt、审计或 Outbox；授权拒绝继续沿用 M70 的拒绝审计；
- 每一页重新读取 Task 并实时鉴权，RoleGrant 撤销后立即失败关闭；
- cursor 解码失败、taskId 不匹配或非法 limit 返回 400，不猜测游标或默认到其他资源；
- Task 不存在或跨 tenant 返回 404，已存在但当前主体无范围返回 403。

## 5. 明确未实现

M72 不实现 HUMAN 命令时间线、Workflow Node 历史、跨模块工单统一时间线、错误正文授权下载、
Attempt 人工 retry/manual-complete、SLA 聚合或 Portal。它也不把 `resultRef` 解引用为业务对象。
