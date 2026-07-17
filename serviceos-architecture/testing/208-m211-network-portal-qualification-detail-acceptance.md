---
title: M211 Network Portal 资质详情只读 UI 验收矩阵
status: Implemented
milestone: M211
lastUpdated: 2026-07-17
---

# M211 Network Portal 资质详情只读 UI 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M211-01 | 列表资质 ID 深链到 `/network-portal/qualifications/:id` | 详情壳可见 | pass（network-portal-qualification-detail.spec.ts） |
| M211-02 | 详情调用 GET technician-qualifications/{id} | 展示 status/decided*/version | pass（E2E stub） |
| M211-03 | 详情页无 decide/approve 写控件 | 只读 | pass（E2E） |
| M211-04 | OpenAPI 仍 0.99.0；Flyway 仍 100/102；catalog 仍 v15 | 无契约/迁移/registry 膨胀 | pass（preflight） |
