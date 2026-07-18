---
title: M216 Network Portal 工作区当前师傅 fan-in 验收矩阵
status: Implemented
milestone: M216
lastUpdated: 2026-07-17
---

# M216 Network Portal 工作区当前师傅 fan-in 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M216-01 | 工作区 fan-in technicians | 展示当前师傅 displayName | pass（E2E） |
| M216-02 | 师傅行深链 membership 详情 | href 含 membershipId | pass（E2E） |
| M216-03 | 未指派任务深链 tasks?taskId= | URL 含水合 query | pass（E2E） |
| M216-04 | 预约行展示 window（非地址） | 可见 start/end/timezone；无 addressRef | pass（E2E） |
| M216-05 | 缺 technician.readOwnNetwork（403） | 省略当前师傅区块 | pass（E2E） |
| M216-06 | OpenAPI 仍 1.0.0；Flyway 仍 100/102；catalog 仍 v16 | 无契约/迁移/registry 膨胀 | pass（preflight） |
