---
title: M218 Technician Portal Feed/日程 Accepted 字段展示验收矩阵
status: Implemented
milestone: M218
lastUpdated: 2026-07-17
---

# M218 Technician Portal Feed/日程 Accepted 字段展示验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M218-01 | Feed 展示 Accepted 字段与 asOf | 可见 stageCode/taskType 等 | pass（E2E） |
| M218-02 | Feed taskId 深链 schedule?taskId= | href 正确 | pass（E2E） |
| M218-03 | Schedule 水合 taskId 并展示 windowEnd/timezone | 过滤高亮 + 字段可见 | pass（E2E） |
| M218-04 | SyncSummary 计数深链 Feed/Schedule | href 正确 | pass（E2E） |
| M218-05 | OpenAPI 仍 1.0.0；Flyway 仍 100/102；catalog 仍 v16 | 无契约膨胀 | pass（preflight） |
