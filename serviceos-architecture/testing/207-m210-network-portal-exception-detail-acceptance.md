---
title: M210 Network Portal 运营异常详情只读 UI 验收矩阵
status: Implemented
milestone: M210
lastUpdated: 2026-07-17
---

# M210 Network Portal 运营异常详情只读 UI 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M210-01 | 列表异常 ID 深链到 `/network-portal/exceptions/:id` | 详情壳可见 | pass（network-portal-exception-detail.spec.ts） |
| M210-02 | 详情调用 GET operational-exceptions/{id} | 展示 severity/status/errorCode/allowedActions=[] | pass（E2E stub） |
| M210-03 | 详情任务深链（有 taskId） | 指向 `/network-portal/tasks?taskId=` | pass（E2E） |
| M210-04 | 详情页无 ACK/resolve 写控件 | 只读 | pass（E2E） |
| M210-05 | OpenAPI 仍 0.99.0；Flyway 仍 100/102；catalog 仍 v15 | 无契约/迁移/registry 膨胀 | pass（preflight） |
