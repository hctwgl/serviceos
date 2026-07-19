---
title: M297 出站提审 Connector SPI 验收矩阵
status: Implemented
milestone: M297
lastUpdated: 2026-07-19
---

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M297-01 | SPI 与管道存在 | `OutboundSubmissionConnector` / `OutboundSubmissionPipeline` 存在且 Modulith `spi` 暴露 | ArchitectureTest |
| M297-02 | 技术 ACK 模型 | ACCEPTED / REJECTED / UNKNOWN 可区分；UNKNOWN 无业务 ackReason | OutboundSubmissionSpiTest |
| M297-03 | BYD errno=0 | 解释为技术接受 `BYD_ERRNO_0` / `BYD_ACCEPTED` | BydOutboundSubmissionConnectorTest |
| M297-04 | BYD errno≠0 | 解释为技术拒绝，不得 UNKNOWN | BydOutboundSubmissionConnectorTest |
| M297-05 | 非 2xx / 非法正文 | 解释为 UNKNOWN | BydOutboundSubmissionConnectorTest |
| M297-06 | 传输 NOT_SENT vs UNKNOWN | 映射 `OutboundTransportResult` 对应 Kind | BydOutboundSubmissionConnectorTest |
| M297-07 | 缺凭证 | preflight 返回 `BYD_CREDENTIAL_VERSION_NOT_CONFIGURED` | BydOutboundSubmissionConnectorTest |
| M297-08 | 出站队列回归 | 既有 Delivery 队列/授权查询不回归 | OutboundDeliveryQueuePostgresIT |
| M297-09 | 核心域无 OEM import | 核心包不得依赖 byd/referenceoem | ArchitectureTest |
