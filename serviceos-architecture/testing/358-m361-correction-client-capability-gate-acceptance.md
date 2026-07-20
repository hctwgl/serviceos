---
title: M361 整改路径客户端能力门禁验收矩阵
status: Implemented
milestone: M361
lastUpdated: 2026-07-19
---

# M361 整改路径客户端能力门禁验收矩阵

| ID | 级别 | 场景 | 期望 | 证据 |
|---|---|---|---|---|
| M361-COR-001 | P0 | iOS listSlots 遇不兼容槽位 | 422 `CLIENT_CAPABILITY_UNSUPPORTED` | ServiceTest + ControllerSecurity |
| M361-COR-002 | P0 | WEB listSlots 调用门禁 | gate 被调用 | ServiceTest |
| M361-COR-003 | P0 | begin/finalize/snapshot/resubmit 透传 clientKind | 签名含 clientKind | ControllerSecurity mocks |
| M361-COR-004 | P0 | OpenAPI 1.0.54 | 整改资料路径登记 422 | contracts + client-ts |
| M361-COR-005 | P0 | ArchitectureTest | evidence → configuration::api | arch |
| M361-COR-006 | P1 | H5 展示服务端 detail | 既有 `userFacingError` | technician-web CorrectionPage |

## 明确不在本矩阵

- iOS 条件执行器；派单过滤；REVIEW_TASK 模板分离；Network Portal on-behalf 门禁。
