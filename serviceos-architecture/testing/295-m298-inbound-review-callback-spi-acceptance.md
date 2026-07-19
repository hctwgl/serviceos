---
title: M298 入站审核回调 SPI 验收矩阵
status: Implemented
milestone: M298
lastUpdated: 2026-07-19
---

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M298-01 | SPI/管道存在 | `ReviewCallbackMappedItem` + `InboundReviewCallbackItemPipeline` | ArchitectureTest |
| M298-02 | domainResult 白名单 | 仅 APPROVED/REJECTED | ReviewCallbackMappedItemTest |
| M298-03 | BYD 映射保持 | result 1/2、字段失败关闭 | BydCpimReviewCallbackMapperTest |
| M298-04 | ReviewCase 回归 | 既有回执/Case 路径不回归 | ReviewCasePostgresIT |
| M298-05 | 核心域无 OEM import | 架构门禁 | ArchitectureTest |
