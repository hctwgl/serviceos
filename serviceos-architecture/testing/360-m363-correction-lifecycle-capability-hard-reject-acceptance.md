---
title: M363 整改领取/启动能力硬拒单验收矩阵
status: Implemented
milestone: M363
lastUpdated: 2026-07-20
---

# M363 整改领取/启动能力硬拒单验收矩阵

| ID | 级别 | 场景 | 期望 | 证据 |
|---|---|---|---|---|
| M363-COR-001 | P0 | iOS claim 遇不兼容源 Task | 422；不调用 humanTasks.claim | ServiceTest |
| M363-COR-002 | P0 | WEB start 遇不兼容源 Task | 422；不调用 humanTasks.start | ServiceTest |
| M363-COR-003 | P0 | claim HTTP 422 | errorCode=`CLIENT_CAPABILITY_UNSUPPORTED` | ControllerSecurity |
| M363-COR-004 | P0 | OpenAPI 1.0.56 | claim/start 登记 422 | contracts + client-ts |
| M363-COR-005 | P0 | ArchitectureTest | evidence → configuration::api | arch |

## 明确不在本矩阵

- 列表整表拒单；Network Portal on-behalf；iOS 条件执行器；派单过滤；REVIEW_TASK。
