---
title: M212 Network Portal 师傅关系详情只读 UI 验收矩阵
status: Implemented
milestone: M212
lastUpdated: 2026-07-17
---

# M212 Network Portal 师傅关系详情只读 UI 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M212-01 | 师傅列表关系 ID 深链到详情 | 详情壳可见 | pass（network-portal-membership-detail.spec.ts） |
| M212-02 | 详情调用 GET technician-memberships/{id} | 展示 status/version/terminate* | pass（E2E stub） |
| M212-03 | OpenAPI 仍 0.99.0；Flyway 仍 100/102；catalog 仍 v15 | 无契约/迁移/registry 膨胀 | pass（preflight） |
