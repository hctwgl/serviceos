---
title: M71 Task 服务端允许动作投影
status: Implemented
milestone: M71
---

# M71 Task 服务端允许动作投影

## 1. 目标

实现 `GET /api/v1/tasks/{taskId}/allowed-actions`，让 Portal 使用服务端投影决定当前主体可渲染的
Task 动作。响应只覆盖已经实施的 `claim/start/complete/release` 命令，不增加新状态或写入口。

## 2. 权威判定

- 先复用 M70 Task 详情读取完成 tenant 隔离和实时 `task.read` 鉴权；
- 动作 capability 使用与写命令完全相同的 `task.claim/task.start/task.complete/task.release`；
- `claim` 仅适用于 READY HUMAN Task、当前主体 ACTIVE CANDIDATE、无 ACTIVE execution guard；
- `start/release` 仅适用于 CLAIMED HUMAN Task、当前主体同时是 `claimedBy` 与 ACTIVE RESPONSIBLE、
  无 ACTIVE execution guard；
- `complete` 仅适用于 RUNNING、流程节点支撑的 HUMAN Task，且当前主体是 `claimedBy` 与 ACTIVE
  RESPONSIBLE、无 ACTIVE execution guard；
- capability、责任、状态或 guard 任一不满足时不返回该动作；写命令仍重新执行完整授权、版本、
  状态、完成条件和幂等校验，allowed-actions 不是授权凭证。

## 3. 契约

响应包含 Task `resourceVersion`、稳定排序的 action descriptor 和服务端 `asOf`。descriptor 使用现有
capability 作为 `code`；需要请求体的动作通过 Core OpenAPI JSON Pointer 指向既有 schema，release
声明 `REQUIRE_REASON`，complete 声明 `REQUIRE_RESULT`。

## 4. 可靠性与边界

- 查询不写审计成功记录，也不为每个缺失动作制造拒绝审计；资源读取被拒绝时仍沿用 M70 拒绝审计；
- 查询与后续命令之间允许事实变化，客户端必须携带返回的版本作为 `If-Match`，命令以数据库条件更新
  失败关闭；
- 不缓存 RoleGrant、责任或 guard，不信任 JWT capability 和客户端状态；
- Task 模块只读取自身表，不跨模块访问内部 Repository 或表。

## 5. 明确未实现

block/resolve-block、retry、cancel、manual-complete、动态业务 actionCode、完成条件预演、Node/Attempt
历史、SLA 聚合和 Portal 不在 M71 范围；这些能力不得以空输入、默认动作或通用命令兜底。
