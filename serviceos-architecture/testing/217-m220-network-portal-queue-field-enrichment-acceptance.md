---
title: M220 Network Portal 队列/列表 Accepted 字段展示验收矩阵
status: Implemented
milestone: M220
lastUpdated: 2026-07-17
---

# M220 Network Portal 队列/列表 Accepted 字段展示验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M220-01 | 整改列表展示 projectId/resubmissionCount 等 | 列可见 | pass（E2E） |
| M220-02 | 整改 correctionTaskId 深链 tasks | href 正确 | pass（E2E） |
| M220-03 | 异常列表展示 category/workOrderId 等并深链 | 列+href | pass（E2E） |
| M220-04 | 异常详情 handlingTaskId 深链 | href 正确 | pass（E2E） |
| M220-05 | 资质列表展示 decided*/version；无 decide 控件 | 可见且无按钮 | pass（E2E） |
| M220-06 | 师傅列表展示 principalId/validFrom/validTo | 列可见 | pass（E2E） |
| M220-07 | OpenAPI 仍 1.0.0；Flyway 仍 100/102；catalog 仍 v16 | 无契约膨胀 | pass（preflight） |
