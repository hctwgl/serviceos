---
title: M362 整改列表/头级客户端能力预检验收矩阵
status: Implemented
milestone: M362
lastUpdated: 2026-07-19
---

# M362 整改列表/头级客户端能力预检验收矩阵

| ID | 级别 | 场景 | 期望 | 证据 |
|---|---|---|---|---|
| M362-COR-001 | P0 | iOS list 遇源 Task 不兼容 Bundle | 项保留且带 `clientCapabilityUnsupportedDetail` | ServiceTest |
| M362-COR-002 | P0 | UNKNOWN list | detail 为 null（Probe 跳过） | ServiceTest |
| M362-COR-003 | P0 | list/claim/start 透传 clientKind | Controller 传入 KIND_ATTRIBUTE | ControllerSecurity |
| M362-COR-004 | P0 | OpenAPI 1.0.55 | TechnicianCorrection 含可选 detail | contracts + client-ts |
| M362-COR-005 | P0 | ArchitectureTest | evidence → configuration::api | arch |
| M362-COR-006 | P1 | H5 Feed 阻断深链 | 有 detail 时不渲染「查看整改要求」 | technician-web FeedPage |
| M362-COR-007 | P1 | H5 详情阻断领取/补传 | 有 detail 时隐藏 lifecycle/evidence | technician-web CorrectionPage |

## 明确不在本矩阵

- claim/start 服务端硬 422；Network Portal on-behalf 门禁；iOS 条件执行器；派单过滤；REVIEW_TASK 模板分离。
