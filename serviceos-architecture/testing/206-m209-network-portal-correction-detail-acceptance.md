---
title: M209 Network Portal 整改详情只读 UI 验收矩阵
status: Implemented
milestone: M209
lastUpdated: 2026-07-17
---

# M209 Network Portal 整改详情只读 UI 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M209-01 | 列表案例 ID 深链到 `/network-portal/corrections/:id` | 详情壳可见 | pass（network-portal-correction-detail.spec.ts） |
| M209-02 | 详情调用 GET correction-cases/{id} | 展示 status/source snapshot/resubmissions | pass（E2E stub） |
| M209-03 | 详情任务代补深链 | 指向 `/network-portal/tasks?taskId=` | pass（E2E） |
| M209-04 | 伪造 NETWORK 上下文 | 失败关闭文案 | pass（E2E / 队列回归） |
| M209-05 | 详情页无 close/waive 写控件 | 只读 | pass（页面审查 + E2E 无写按钮） |
| M209-06 | OpenAPI 仍 0.99.0；Flyway 仍 100/102；catalog 仍 v15 | 无契约/迁移/registry 膨胀 | pass（preflight / CodePageRegistry） |
