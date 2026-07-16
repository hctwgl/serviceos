---
title: M84 工单时间线投影 Checkpoint 与重建
status: Implemented
milestone: M84
---

# M84 工单时间线投影 Checkpoint 与重建

## 1. 目标

在 DATA-06 已接受的投影运行时窄切片上，为现有
`readmodel.work-order-core-timeline.v1` 建立可验证的 checkpoint、投影 dead letter 与
generation 重建切换，使时间线查询的 `freshnessStatus` 不再永久伪造为 `UNKNOWN`。

本里程碑只覆盖工单时间线这一投影；不实现工作区/队列/SavedView/搜索或 Portal。

## 2. 模块与事实边界

- `readmodel` 拥有 `rdm_projection_state`、`rdm_projection_checkpoint`、
  `rdm_projection_dead_letter` 与时间线条目的 `rebuild_generation`；
- 重建扫描已发布 Outbox 事件时，只通过 `reliability::spi` 的
  `PublishedOutboxEventReader` 读取，禁止 readmodel 直接访问 `rel_*` 表；
- 实时消费仍走 Inbox；重建写入新 generation 时绕过 Inbox（避免与实时 consumer 冲突），
  以 `(tenant_id, source_event_id, rebuild_generation)` 幂等；
- 查询只读取 `active_generation`；重建未完成验证前不得切换，不能把空表当“无业务数据”。

## 3. 运行时语义

| 概念 | 语义 |
|---|---|
| projection_code | 固定 `work-order-core-timeline.v1` |
| partition_key | 租户内固定 `*`（单分区） |
| checkpoint | 实时/重建成功投影后推进；记录 lastOutboxId、lastOccurredAt、processedAt、status |
| dead letter | 重建失败关闭时登记 eventId/digest/error；修复后可幂等重放 |
| rebuild | 创建 generation+1 → 扫描 PUBLISHED 源事件重投影 → 验证 → 原子切换 activeGeneration |

`freshnessStatus`：

- `UNKNOWN`：该租户尚无任何 checkpoint；
- `REBUILDING`：投影状态为重建中；
- `LAGGING`：存在未解决 dead letter，或投影状态 FAILED；
- `FRESH`：RUNNING 且有 checkpoint、无未解决 dead letter。

## 4. 契约与迁移

- Core OpenAPI **0.55.0**：`WorkOrderTimelinePage.freshnessStatus` 扩展为
  `FRESH | LAGGING | UNKNOWN | REBUILDING`；
- Flyway **V077**：时间线 `rebuild_generation`、三张投影运行时表、初始 active generation=1。

## 5. 明确未实现

Broker 端到端 offset、多投影通用平台、工作区/队列/SavedView/搜索、试算/结算、Portal、
revision/slots 技术噪声、ServiceNetwork、Admin 重建 HTTP API（重建由应用服务/测试可调用作业证明）。
