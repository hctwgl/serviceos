---
title: M236 Network Portal 目录页工单头字段验收矩阵
status: Accepted
milestone: M236
lastUpdated: 2026-07-17
---

# M236 验收矩阵（ADR-074）

| ID | 场景 | 期望 | 证据 |
| --- | --- | --- | --- |
| M236-01 | 本网点目录 | items 含 serviceProductCode/区域/receivedAt | pass（PostgresIT） |
| M236-02 | 工单缺失 | 头字段为 null，列表仍成功 | pass（PostgresIT/夹具） |
| M236-03 | 他网点工单头 | 不出现在本网点目录 | pass（PostgresIT） |
| M236-04 | 不含客户 PII | 无 name/mobile/address/vin | pass（契约/代码） |
| M236-05 | Admin Web 列展示 | E2E | pass（E2E） |
| M236-06 | OpenAPI 1.0.16；Flyway 100/102；catalog v16 | 预检 | pass（preflight） |
