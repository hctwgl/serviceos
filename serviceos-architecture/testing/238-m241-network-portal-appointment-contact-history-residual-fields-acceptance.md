---
title: M241 Network Portal 预约/联系历史残余 Accepted 字段展示验收矩阵
status: Accepted
milestone: M241
lastUpdated: 2026-07-17
---

# M241 验收矩阵（ADR-079）

| ID | 场景 | 期望 | 证据 |
| --- | --- | --- | --- |
| M241-01 | 预约历史有数据 | 展示 project/workOrder/network/technician/createdAt/allowedActions/时长 | pass（E2E） |
| M241-02 | 联系历史有数据 | 展示 project/workOrder/createdAt | pass（E2E） |
| M241-03 | 敏感字段 | 不展示 addressRef/note/party/recording | pass（E2E） |
| M241-04 | 无新契约 | OpenAPI 仍 1.0.16；Flyway 100/102；catalog v16 | pass（preflight） |
