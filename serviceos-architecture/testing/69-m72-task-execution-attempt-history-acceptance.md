---
title: M72 Task 执行 Attempt 历史查询验收矩阵
status: Implemented
milestone: M72
---

# M72 Task 执行 Attempt 历史查询验收矩阵

| 编号 | 场景 | 预期证据 |
|---|---|---|
| M72-01 | 自动 Task 存在多个 Attempt | 按 `attemptNo DESC` 返回标识、结果码、错误码、结果引用、重试与起止时间 |
| M72-02 | 稳定分页 | limit+1 判定下一页，cursor 只读取更小 attemptNo，不重复、不遗漏既有历史 |
| M72-03 | cursor 跨 Task 或非法 limit | 返回 400，不猜测或回退 |
| M72-04 | HUMAN Task 无 Attempt | 返回带当前 resourceVersion 的空页 |
| M72-05 | 实时授权 | project/tenant `task.read` 可读；撤权后 403 并记录拒绝审计 |
| M72-06 | tenant 隔离 | 跨 tenant Task 返回 404，不泄露 Attempt 是否存在 |
| M72-07 | 信息最小化 | 响应不包含 workerId、payload、错误正文、私有响应或凭据 |
| M72-08 | HTTP 契约 | 401、ETag、correlation ID、分页参数和响应 Schema 均受自动化验证 |
| M72-09 | 工程门禁 | Core OpenAPI 0.43.0、PostgreSQL/MVC/Contract/Client/ArchitectureTest/L3 通过；Flyway 保持 V070/72 |

本矩阵不验收 HUMAN 命令时间线、Workflow Node 历史、跨模块统一时间线、Attempt 写命令、SLA 聚合
或 Portal。
