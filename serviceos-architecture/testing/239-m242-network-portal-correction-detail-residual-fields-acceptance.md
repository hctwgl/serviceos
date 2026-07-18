---
title: M242 Network Portal 整改详情残余 Accepted 字段展示验收矩阵
status: Accepted
milestone: M242
lastUpdated: 2026-07-17
---

# M242 验收矩阵（ADR-080）

| ID | 场景 | 期望 | 证据 |
| --- | --- | --- | --- |
| M242-01 | WAIVED 详情 | 展示 closedBy/waivedBy/waiveApprovalRef/waiveNote | pass（E2E） |
| M242-02 | 补传历史 | 展示 submittedBy | pass（E2E） |
| M242-03 | 无写控件 | 不出现 close/waive/补传按钮 | pass（E2E） |
| M242-04 | 无新契约 | OpenAPI 仍 1.0.16；Flyway 100/102；catalog v16 | pass（preflight） |
