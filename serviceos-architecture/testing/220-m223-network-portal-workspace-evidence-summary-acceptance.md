---
title: M223 Network Portal 工作区 Evidence 槽位/资料项摘要验收矩阵
status: Implemented
milestone: M223
lastUpdated: 2026-07-17
---

# M223 Network Portal 工作区 Evidence 槽位/资料项摘要验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M223-01 | NETWORK evidence.read + ACTIVE 任务已解析槽位 | evidenceSlots 含摘要且无 definition JSON | pass（PostgresIT） |
| M223-02 | 缺 evidence.read | JSON 省略 evidenceSlots 与 evidenceItems | pass（PostgresIT） |
| M223-03 | 他网点任务槽位/资料项不计入 | 仅 ACTIVE taskIds | pass（PostgresIT） |
| M223-04 | NETWORK evidence.read + ACTIVE 任务资料项 | evidenceItems 无 file/captureMetadata | pass（PostgresIT） |
| M223-05 | 有能力但无解析/无数据 | 两数组均为 [] | pass（PostgresIT） |
| M223-06 | Admin Web 展示/省略 | E2E | pass（E2E） |
| M223-07 | OpenAPI 1.0.3；Flyway 100/102；catalog v16 | 契约/预检 | pass（preflight） |
