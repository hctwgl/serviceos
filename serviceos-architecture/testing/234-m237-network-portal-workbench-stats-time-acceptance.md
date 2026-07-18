---
title: M237 Network Portal 工作台统计时间展示验收矩阵
status: Accepted
milestone: M237
lastUpdated: 2026-07-17
---

# M237 验收矩阵（ADR-075）

| ID | 场景 | 期望 | 证据 |
| --- | --- | --- | --- |
| M237-01 | 工作台有 capacity | 页级展示「统计时间」= `asOf` | pass（E2E） |
| M237-02 | 容量行 | 展示「更新时间」= `updatedAt` | pass（E2E） |
| M237-03 | 无新契约 | OpenAPI 仍 1.0.16；Flyway 100/102；catalog v16 | pass（preflight） |
| M237-04 | 无写控件 | 不出现产能申请/ACK/decide | pass（代码审查/E2E） |
