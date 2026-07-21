---
title: M417 Admin 师傅服务关系并入主体变更时间线验收矩阵
version: 0.1.0
status: Implemented
milestone: M417
lastUpdated: 2026-07-21
---

# M417 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 创建/终止合并 | `TECHNICIAN_MEMBERSHIP_CREATED` / `TERMINATED` 可见；摘要含网点名 | `IdentityDirectoryPostgresIT` |
| A2 | soft-omit | 缺 `network.read` 时 `omittedSources` 含 `TECHNICIAN_MEMBERSHIP`（及共用门禁的 `NETWORK_MEMBERSHIP`） | 同上 |
| A3 | 模块边界 | network 仅依赖 `identity :: api`；ArchitectureTest 通过 | ArchitectureTest |
| A4 | Admin UI | 变更记录展示「师傅服务关系」与建立摘要 | Playwright |

产品状态：`READY_FOR_REVIEW`。
