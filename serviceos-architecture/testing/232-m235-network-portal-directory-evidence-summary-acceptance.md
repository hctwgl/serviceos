---
title: M235 Network Portal 目录页资料 Evidence 服务端摘要验收矩阵
status: Accepted
milestone: M235
lastUpdated: 2026-07-17
---

# M235 验收矩阵（ADR-073）

| ID | 场景 | 期望 | 证据 |
| --- | --- | --- | --- |
| M235-01 | NETWORK evidence.read + 目录资料 | 页含 `evidenceSlots`/`evidenceItems` | pass（PostgresIT） |
| M235-02 | 缺 evidence.read | 同时省略两属性 | pass（PostgresIT） |
| M235-03 | 有能力无资料 | 两属性均为 `[]` | pass（PostgresIT） |
| M235-04 | 他网点资料不计 | 仅本页 taskIds | pass（PostgresIT） |
| M235-05 | Admin Web 资料列展示/省略 | E2E | pass（E2E） |
| M235-06 | OpenAPI 1.0.15；Flyway 100/102；catalog v16 | 预检 | pass（preflight） |
