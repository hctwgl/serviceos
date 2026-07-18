---
title: M239 Network Portal 工作区 Visit/表单/Evidence Accepted 字段展示验收矩阵
status: Accepted
milestone: M239
lastUpdated: 2026-07-17
---

# M239 验收矩阵（ADR-077）

| ID | 场景 | 期望 | 证据 |
| --- | --- | --- | --- |
| M239-01 | Visit 摘要有数据 | 展示 appointment/technician/network/check-in-out/result/version | pass（E2E） |
| M239-02 | 表单提交摘要有数据 | 展示 project/formVersion/submittedAt/digest | pass（E2E） |
| M239-03 | Evidence 槽位有数据 | 展示 template/required/min-max/active/transition/disposition/resolved | pass（E2E） |
| M239-04 | Evidence 资料项有数据 | 展示 project 与 latestRevisionNumber | pass（E2E） |
| M239-05 | 无新契约 | OpenAPI 仍 1.0.16；Flyway 100/102；catalog v16 | pass（preflight） |
| M239-06 | 无敏感字段 | 不展示 GPS/note/values/definition/file | pass（代码审查/E2E） |
