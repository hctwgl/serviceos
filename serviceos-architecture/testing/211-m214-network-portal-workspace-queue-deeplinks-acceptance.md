---
title: M214 Network Portal 工作区协作队列深链与 query 水合验收矩阵
status: Implemented
milestone: M214
lastUpdated: 2026-07-17
---

# M214 Network Portal 工作区协作队列深链与 query 水合验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M214-01 | 工作区任务行深链 tasks/corrections/exceptions（带 taskId） | URL 含 query | pass（E2E） |
| M214-02 | Tasks 页水合 `?taskId=` | 选中该任务 | pass（E2E） |
| M214-03 | Corrections 页水合 `?taskId=` | list 调用带过滤且展示提示 | pass（E2E） |
| M214-04 | Exceptions 页水合 `?taskId=` | list 调用带过滤且展示提示 | pass（E2E） |
| M214-05 | 工作区 related 整改/异常摘要 | 有数据时深链详情；缺能力省略 | pass（E2E stub） |
| M214-06 | OpenAPI 仍 1.0.0；Flyway 仍 100/102；catalog 仍 v16 | 无契约/迁移/registry 膨胀 | pass（preflight） |
