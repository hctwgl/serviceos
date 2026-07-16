---
title: M86 工单时间线投影运行时硬化
status: Implemented
milestone: M86
---

# M86 工单时间线投影运行时硬化

## 1. 目标

在 DATA-06 已接受、M84 已落地的 `work-order-core-timeline.v1` 运行时上，补齐三类硬化能力：

1. `projection_definition` 权威登记；
2. dead letter 按 eventId 幂等重放（源事件缺失则 DISCARDED）；
3. 切换成功后清理旧 generation，以及 FAILED 恢复后清理孤儿 generation。

## 2. 接受范围

- DATA-06 §2 `projection_definition`、dead letter「修复后按 eventId 幂等 replay」；
- DATA-06 §13 切换后清理旧 generation（本切片不引入长观察窗，切换成功即清理）；
- 仍仅针对 `work-order-core-timeline.v1`。

## 3. 运行时语义

| 能力 | 语义 |
|---|---|
| definition | 登记 projectionCode、schemaVersion、sourceEventTypes、partitionStrategy、rebuildPolicy、freshnessTarget、ownerModule |
| replay | 打开 PENDING dead letter → 经 `PublishedOutboxEventReader.findPublishedByEventId` 取源 → `projectForRebuild` → `REPLAYED`；源缺失 → `DISCARDED` |
| recover | 无未解决 dead letter 且状态 FAILED 时恢复 RUNNING，并删除 `rebuild_generation > active` 的孤儿投影/checkpoint |
| cleanup | 重建切换成功后删除 `rebuild_generation < active` 的条目与 checkpoint；dead letter 审计行保留 |

## 4. 契约与迁移

- 无新公开 HTTP；Core OpenAPI 保持 **0.56.0**；
- Flyway **V078**：`rdm_projection_definition` + seed；迁移总数 80。

## 5. 明确未实现

多投影通用平台、Admin 重建/重放 HTTP、Broker offset、长观察窗策略、队列/SavedView/Portal、试算/结算。
