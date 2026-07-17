---
title: M208 Network Portal 产能页验收矩阵
status: Implemented
milestone: M208
lastUpdated: 2026-07-17
---

# M208 Network Portal 产能页验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M208-01 | Page Registry 含 `NETWORK.CAPACITY` + `networkTask.read` | catalog v15 | pass（PortalContextPostgresIT） |
| M208-02 | Admin Web `/network-portal/capacity` 调用 GET capacity | 列表含 version | pass（network-portal-capacity-page.spec.ts） |
| M208-03 | 工作台 capacity 深链到产能页 | 链接可达 | pass（network-portal-capacity-page.spec.ts / workbench） |
| M208-04 | 伪造 NETWORK 上下文 | 失败关闭文案 | pass（E2E） |
| M208-05 | 既有 capacity API 未认证 | 401 | pass（NetworkPortalControllerSecurityTest 回归） |
| M208-06 | OpenAPI 仍 0.99.0；Flyway 仍 100/102 | 无契约/迁移膨胀 | pass（contracts / preflight） |
| M208-07 | ArchitectureTest | 模块边界 | pass（ArchitectureTest） |
