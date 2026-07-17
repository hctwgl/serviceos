---
title: M215 Network Portal 工作区预约/联系尝试 fan-in 验收矩阵
status: Implemented
milestone: M215
lastUpdated: 2026-07-17
---

# M215 Network Portal 工作区预约/联系尝试 fan-in 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M215-01 | 工作区按 taskIds fan-in appointments | 展示摘要行 | pass（E2E） |
| M215-02 | 工作区按 taskIds fan-in contact-attempts | 展示摘要行 | pass（E2E） |
| M215-03 | 预约/联系行深链 tasks?taskId= | URL 含 query | pass（E2E） |
| M215-04 | 缺 manageAppointment（403） | 省略预约/联系区块 | pass（页面逻辑 + 可选 E2E） |
| M215-05 | OpenAPI 仍 1.0.0；Flyway 仍 100/102；catalog 仍 v16 | 无契约/迁移/registry 膨胀 | pass（preflight） |
