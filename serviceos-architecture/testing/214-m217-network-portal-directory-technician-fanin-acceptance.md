---
title: M217 Network Portal 目录页师傅 fan-in 验收矩阵
status: Implemented
milestone: M217
lastUpdated: 2026-07-17
---

# M217 Network Portal 目录页师傅 fan-in 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M217-01 | 工单目录解析师傅 displayName | 可见姓名 | pass（E2E） |
| M217-02 | 任务目录解析师傅 displayName + 工单深链工作区 | 姓名 + href | pass（E2E） |
| M217-03 | 工作台 ACTIVE 工单/任务/师傅计数深链 | href 正确 | pass（E2E） |
| M217-04 | 整改 correctionTaskId / 异常 workOrderId 深链 | href 正确 | pass（E2E） |
| M217-05 | OpenAPI 仍 1.0.0；Flyway 仍 100/102；catalog 仍 v16 | 无契约膨胀 | pass（preflight） |
