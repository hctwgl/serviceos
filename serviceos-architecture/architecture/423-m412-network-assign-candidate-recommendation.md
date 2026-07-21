---
title: M412 Network 分配候选推荐解释
version: 0.1.0
status: Implemented
milestone: M412
lastUpdated: 2026-07-21
---

# M412 Network 分配候选推荐解释

## 1. 目标

关闭 Network 分配抽屉 `UI_DATA_GAP`「推荐解释」：候选卡与影响摘要必须展示可由服务端证明的推荐档位与中文解释，不得前端猜测，也不得暴露内部评分公式。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.78** `recommendationTier` / `recommendationSummary` / `recommendationReasons`；Page `rankingExplanation` / `emptyReason` |
| ReadModel | 基于可分配、资质、日程冲突、行政区亲和、产能与开放任务汇总档位 |
| Network Web | 分配抽屉候选卡与影响区展示推荐解释；空列表使用服务端 `emptyReason` |

推荐档位：

- `RECOMMENDED`：同区/同城覆盖 + 已通过资质 + 无窗口重叠 + 产能可用（或未知）
- `ACCEPTABLE`：可分配且无谨慎因素，但未达优先条件（如仅同省）
- `CAUTION`：覆盖未命中/区域未知/无已通过资质/窗口重叠/产能已满等
- `NOT_ASSIGNABLE`：师傅关系或档案非 ACTIVE

## 3. 明确未实现

- 经纬度/路网米制距离与 ETA
- 机器学习/加权数值评分
- 完整预约日历视图
- 产品负责人视觉金标（状态保持 `READY_FOR_REVIEW`）

## 4. 权限与边界

- 仍要求 `networkTask.read` + `technician.readOwnNetwork`
- 仅本网点 ACTIVE 师傅；不暴露其他网点候选
- 排序说明对操作员透明，不含内部公式
