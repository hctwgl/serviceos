---
title: M70 授权任务队列与详情
status: Implemented
milestone: M70
---

# M70 授权任务队列与详情

## 1. 目标

实现 Admin MVP 的 `GET /api/v1/tasks` 与 `GET /api/v1/tasks/{taskId}`。`task.read` 的实时
TENANT/PROJECT/REGION/NETWORK RoleGrant 决定可见任务；列表在 SQL 中收敛范围，不逐行鉴权。

## 2. 查询语义

- 支持 `projectId/taskKind/status/assignee=me` 精确筛选；其他 assignee 值非法；
- 按 `priority DESC, nextRunAt, createdAt, taskId` 稳定分页，cursor 绑定授权范围与全部筛选；
- tenant-wide 可读取同 tenant 的无 project 运营任务；项目范围只能读取映射项目任务；
- 详情先 tenant 隔离读取，再按 project 或 tenant 资源归属实时鉴权；
- 列表不返回 payload、result、inputVersionRefs 或错误正文；详情返回冻结配置/流程/表单引用、
  结果引用和结构化 inputVersionRefs，但仍不返回 payload 正文或执行错误正文。

## 3. 非目标

动态 allowed-actions、Task 写命令扩展、候选人完整历史、执行 Attempt 历史、SLA 聚合、批量操作和 Portal
不在 M70 范围。

## 4. 工程变化

V070 注册 `task.read` 并补齐范围化队列索引；Core OpenAPI 升至 0.41.0。Task 状态机、命令事务、
Inbox/Outbox 和 Evidence 主线不变。
