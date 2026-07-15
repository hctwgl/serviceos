---
title: M71 Task 服务端允许动作投影验收矩阵
status: Implemented
milestone: M71
---

# M71 Task 服务端允许动作投影验收矩阵

| 编号 | 场景 | 预期证据 |
|---|---|---|
| M71-01 | READY HUMAN + ACTIVE CANDIDATE + task.claim | 只返回 `task.claim` |
| M71-02 | claim 后当前责任人 | 返回 `task.start` 与 `task.release`，版本同步递增 |
| M71-03 | start 后当前责任人 | 只返回 `task.complete` |
| M71-04 | capability 实时撤销 | 对应动作立即消失，不读取 JWT 缓存 |
| M71-05 | ACTIVE execution guard | 所有人工作命令动作均为空 |
| M71-06 | AUTOMATED、终态或非候选/非责任人 | 不返回不适用动作 |
| M71-07 | 无 task.read 或跨 tenant | 403 + 拒绝审计，或跨 tenant 404 |
| M71-08 | HTTP 契约 | 401、resourceVersion、稳定 action descriptor、ETag 与 correlation ID |
| M71-09 | 工程门禁 | OpenAPI 0.42.0、PostgreSQL/MVC/Contract/Client/ArchitectureTest/L3 通过；Flyway 保持 V070/72 |

本矩阵不验收尚未实现的 Task 命令、动作预演、Node/Attempt 历史、SLA 聚合或 Portal。
