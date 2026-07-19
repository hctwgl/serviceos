---
title: M302 入站更新工单 SPI 验收矩阵
status: Implemented
milestone: M302
lastUpdated: 2026-07-19
---

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M302-01 | SPI/管道存在 | Update SPI + Pipeline | ArchitectureTest |
| M302-02 | 映射失败关闭 | 未知字段拒绝 | BydCpimUpdateOrderMapperTest |
| M302-03 | 建单后更新 | 地址/姓名变更；写出 external-details-updated | BydCpimUpdateOrderHttpPostgresIT |
| M302-04 | 更新重放 | REPLAYED | BydCpimUpdateOrderHttpPostgresIT |
| M302-05 | 无工单更新 | WORK_ORDER_NOT_FOUND | BydCpimUpdateOrderHttpPostgresIT |
| M302-06 | 事件契约 | Schema 示例通过 | ContractValidationTest |
