---
title: M416 Admin 网点任职并入主体变更时间线验收矩阵
version: 0.1.0
status: Implemented
milestone: M416
lastUpdated: 2026-07-21
---

# M416 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 邀请/终止合并 | `MEMBERSHIP_INVITED` / `MEMBERSHIP_TERMINATED` 可见；摘要含网点名与角色 | `IdentityDirectoryPostgresIT` |
| A2 | soft-omit | 缺 `network.read` 时 `omittedSources` 含 `NETWORK_MEMBERSHIP`，不造假 | 同上 |
| A3 | 模块边界 | network 仅依赖 `identity :: api`；ArchitectureTest 通过 | ArchitectureTest |
| A4 | Admin UI | 变更记录展示「网点任职」与邀请摘要 | Playwright |

产品状态：`READY_FOR_REVIEW`。
