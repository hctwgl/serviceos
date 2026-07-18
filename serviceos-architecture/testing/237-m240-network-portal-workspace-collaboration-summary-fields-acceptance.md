---
title: M240 Network Portal 工作区协作摘要 Accepted 字段展示验收矩阵
status: Accepted
milestone: M240
lastUpdated: 2026-07-17
---

# M240 验收矩阵（ADR-078）

| ID | 场景 | 期望 | 证据 |
| --- | --- | --- | --- |
| M240-01 | 预约摘要有数据 | 展示 network/technician/version/createdAt | pass（E2E） |
| M240-02 | 联系摘要有数据 | 展示 project/workOrder/started-ended/nextContactAt | pass（E2E） |
| M240-03 | 整改摘要有数据 | 展示 project/decision/correctionTask 深链/时间/最新补传 | pass（E2E） |
| M240-04 | 审核摘要有数据 | 展示 scope/policy/snapshot/refs/最新 decision | pass（E2E） |
| M240-05 | 异常摘要有数据 | 展示 taxonomy/handlingTask 深链/occurrences/时间 | pass（E2E） |
| M240-06 | 师傅摘要有数据 | 展示 principal/profileStatus/valid/version | pass（E2E） |
| M240-07 | 无新契约 | OpenAPI 仍 1.0.16；Flyway 100/102；catalog v16 | pass（preflight） |
| M240-08 | 无敏感字段 | 不展示 actor/note/party/recording/PII | pass（代码审查） |
