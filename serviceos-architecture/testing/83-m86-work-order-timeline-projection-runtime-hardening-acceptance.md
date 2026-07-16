---
title: M86 工单时间线投影运行时硬化验收矩阵
status: Implemented
milestone: M86
---

# M86 工单时间线投影运行时硬化验收矩阵

| ID | 场景 | 期望 |
|---|---|---|
| M86-01 | definition 种子 | `work-order-core-timeline.v1` 行存在且 owner/schema 正确 |
| M86-02 | dead letter 重放成功 | PENDING → REPLAYED；同 event 幂等；freshness 恢复非 LAGGING（若无其他开放 DL） |
| M86-03 | 源事件缺失 | PENDING → DISCARDED；不伪造投影成功 |
| M86-04 | 重建切换后清理 | `rebuild_generation < active` 的条目与 checkpoint 被删除；查询仍 FRESH |
| M86-05 | FAILED 恢复 | 无开放 DL 时恢复 RUNNING，并清理 `> active` 孤儿 generation |
| M86-06 | 工程门禁 | OpenAPI 0.56.0、V078/80、PostgreSQL IT、ArchitectureTest、L3 verify |

不验收 Admin HTTP、多投影平台、Broker offset、Portal。
