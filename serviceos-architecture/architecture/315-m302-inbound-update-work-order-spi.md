---
title: M302 入站更新工单 Connector SPI
status: Implemented
milestone: M302
lastUpdated: 2026-07-19
relatedMilestones: [M267, M300, M301]
---

# M302 入站更新工单 Connector SPI

## 目标

交付外部工单联系/地址更新的通用入站管道，并接入 BYD `update-orders`；领域命令带乐观锁与更新摘要幂等。

## 范围

- `UpdateExternalWorkOrderCommand` + `last_external_update_digest`（Flyway V118）
- `UpdateWorkOrderMappedInbound` / `InboundUpdateWorkOrderPipeline`
- BYD update-orders 端点；事件 `workorder.external-details-updated@v1`；Core OpenAPI 1.0.40

## 明确未实现

- 暂停/恢复/关闭入站；远端状态查询；INTEGRATION Mapping 运行时引擎

## 验证

```bash
bash scripts/agent-verify.sh test ArchitectureTest,BydCpimUpdateOrderMapperTest
bash scripts/agent-verify.sh it BydCpimUpdateOrderHttpPostgresIT
```
