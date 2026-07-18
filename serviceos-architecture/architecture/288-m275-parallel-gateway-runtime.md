---
title: M275 PARALLEL_GATEWAY 分叉与汇聚运行时
status: Implemented
milestone: M275
lastUpdated: 2026-07-18
relatedMilestones: [M269, M270]
---

# M275 PARALLEL_GATEWAY 分叉与汇聚运行时

## 目标

实现并行网关：fork 同时激活多条无条件出边；join 按入边计数汇聚，到齐后沿唯一出边继续。

## 已实现

- 发布期 fork/join 形态校验（分支同 stage、fork 无条件出边、join 单出边）
- 运行时 fork 多分支激活；`wfl_parallel_join` / `wfl_parallel_join_token` 汇聚计数
- 重复 token 失败关闭；未到齐不推进
- Flyway V102

## 明确未实现

包容网关、条件并行出边、跨 stage 并行分支、定时器、子流程、多实例。
