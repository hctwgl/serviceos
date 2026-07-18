---
title: M278 多实例任务运行时
status: Implemented
milestone: M278
lastUpdated: 2026-07-18
relatedMilestones: [M275, M277]
---

# M278 多实例任务运行时

## 目标

支持任务节点 `multiInstance.cardinality`：到达时并行创建 N 个任务实例，全部完成后沿唯一出边继续。

## 已实现

- schema：`multiInstance.cardinality`（2～50）
- Flyway V105：`wfl_multi_instance` / `wfl_multi_instance_slot`
- 激活时创建 N 个 ACTIVE 节点/任务；slot 完成计数；到齐后推进
- PostgreSQL IT

## 明确未实现

集合驱动 cardinality、顺序多实例、完成条件表达式、多实例子流程。
