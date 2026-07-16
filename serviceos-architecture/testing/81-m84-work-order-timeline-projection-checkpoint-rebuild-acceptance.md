---
title: M84 工单时间线投影 Checkpoint 与重建验收矩阵
status: Implemented
milestone: M84
---

# M84 工单时间线投影 Checkpoint 与重建验收矩阵

| ID | 场景 | 期望 |
|---|---|---|
| M84-01 | 实时投影成功 | 推进 checkpoint；查询可返回 FRESH（有 checkpoint 且无 dead letter） |
| M84-02 | 租户尚无 checkpoint | freshnessStatus=UNKNOWN |
| M84-03 | 重建作业 | 新 generation 重投影后原子切换；查询只见 active generation |
| M84-04 | 重建失败 | 写入 dead letter、不切换 generation、freshness=LAGGING；旧 generation 仍可查 |
| M84-05 | 幂等 | 同 event 同 generation 不重复行；Inbox 重放仍安全 |
| M84-06 | 模块边界 | readmodel 仅经 PublishedOutboxEventReader 读已发布事件；ArchitectureTest 通过 |
| M84-07 | 工程门禁 | OpenAPI 0.55.0、V077/79、PostgreSQL IT、Contract/Client、L3 verify |

不验收 Portal、SavedView、搜索、多投影平台、Broker offset。
