---
title: M267 通用 Connector SPI 验收矩阵
status: Implemented
milestone: M267
---

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M267-01 | SPI 与管道存在且被 BYD 调用 | `integration.spi` 与 `InboundCreateWorkOrderPipeline` 非空接口；BYD 入站委托管道 | ArchitectureTest + 代码审查 |
| M267-02 | BYD 建单成功路径 | 验签通过 → Envelope/Canonical → WorkOrder 锁定 Bundle | `BydCpimInboundOrderHttpPostgresIT` |
| M267-03 | 同 transport 重放 | 返回首次结果；不重复建单 | `BydCpimInboundOrderHttpPostgresIT` / ReplayGuard IT |
| M267-04 | 同业务键不同摘要 | REPLAY_CONFLICT / 失败关闭 | `BydCpimInboundOrderHttpPostgresIT` |
| M267-05 | 配置零/多命中 | Envelope REJECTED；不创建工单 | `BydCpimInboundOrderHttpPostgresIT` |
| M267-06 | 核心域防污染 | 核心模块不 import `integration.byd` | `ArchitectureTest` |
| M267-07 | 模块边界 | Spring Modulith `modules.verify()` 通过 | `ArchitectureTest` |
| M267-08 | 无契约破坏 | Core/BYD OpenAPI 与 Flyway 本切片不变 | git diff / docs |
