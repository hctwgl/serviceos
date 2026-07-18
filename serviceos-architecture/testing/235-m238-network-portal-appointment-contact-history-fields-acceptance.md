---
title: M238 Network Portal 预约/联系历史 Accepted 字段展示验收矩阵
status: Accepted
milestone: M238
lastUpdated: 2026-07-17
---

# M238 验收矩阵（ADR-076）

| ID | 场景 | 期望 | 证据 |
| --- | --- | --- | --- |
| M238-01 | 预约历史有数据 | 展示操作者 `createdBy` 与渠道 `confirmationChannel` | pass（E2E） |
| M238-02 | 预约当前 revision | 展示确认方类型与窗口；不展示 addressRef/note | pass（E2E） |
| M238-03 | 联系历史有数据 | 展示操作者 `actorId` 与渠道 `channel` | pass（E2E） |
| M238-04 | 无新契约 | OpenAPI 仍 1.0.16；Flyway 100/102；catalog v16 | pass（preflight） |
