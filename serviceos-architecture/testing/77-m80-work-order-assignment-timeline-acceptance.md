---
title: M80 工单服务分配生命周期时间线事件合并验收矩阵
status: Accepted
milestone: M80
---

# M80 工单服务分配生命周期时间线事件合并验收矩阵

| ID | 场景 | 期望 |
|---|---|---|
| M80-01 | pending/activated/completed/timed-out | 写入 ASSIGNMENT / ServiceAssignment 投影，outcome 取 reasonCode/errorCode |
| M80-02 | 握手事件 schemaVersion=1 | supports 拒绝，不投影 |
| M80-03 | 信封 aggregateId 与载荷不一致 | 失败关闭，不留 Inbox/投影半成品 |
| M80-04 | 信息最小化 | 不保存 assigneeId / capacity / guard / preparedTaskAssignmentId |
| M80-05 | 迁移 | V076 category 含 ASSIGNMENT；当前版本 076 / 78 |
| M80-06 | 工程门禁 | OpenAPI 0.51.0、PostgreSQL/Inbox/MVC/Contract/Client/ArchitectureTest 与 L3 verify |
