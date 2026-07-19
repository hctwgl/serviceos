---
title: M300 入站取消工单 SPI 验收矩阵
status: Implemented
milestone: M300
lastUpdated: 2026-07-19
---

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M300-01 | SPI/管道存在 | Cancel SPI + Pipeline + ExternalLookup | ArchitectureTest |
| M300-02 | 映射失败关闭 | 未知字段/非法日期拒绝 | BydCpimCancelOrderMapperTest |
| M300-03 | 建单后取消 | RECEIVED→CANCELLED；写出 workorder.cancelled | BydCpimCancelOrderHttpPostgresIT |
| M300-04 | 取消重放 | 同 Nonce 返回 REPLAYED | BydCpimCancelOrderHttpPostgresIT |
| M300-05 | 无工单取消 | WORK_ORDER_NOT_FOUND；Envelope REJECTED | BydCpimCancelOrderHttpPostgresIT |
