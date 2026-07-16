---
title: M79 工单运营异常确认时间线事件合并验收矩阵
status: Accepted
milestone: M79
---

# M79 工单运营异常确认时间线事件合并验收矩阵

| ID | 场景 | 期望 |
|---|---|---|
| M79-01 | acknowledged | 经 ExceptionTimelineContextQuery 写入 EXCEPTION 投影 |
| M79-02 | 异常不存在 | 失败关闭，不留 Inbox/投影半成品 |
| M79-03 | 无 Task 链接 | Inbox 完成但不投影 |
| M79-04 | 信息最小化 | 不保存 errorCode、note、handlingTaskId |
| M79-05 | Modulith | readmodel → operations::api 通过 ArchitectureTest |
| M79-06 | 工程门禁 | OpenAPI 0.50.0、PostgreSQL/Inbox/MVC/Contract/Client/ArchitectureTest 与 L3 verify |
