---
title: M410 Network 分配候选行政区距离亲和
version: 0.1.0
status: Implemented
milestone: M410
lastUpdated: 2026-07-21
---

# M410 Network 分配候选行政区距离亲和

## 1. 目标

关闭 Network 分配抽屉 `UI_DATA_GAP` 中的距离读模型缺口：候选卡与影响摘要必须展示可由服务端证明的区域亲和，不得前端猜测或伪造经纬度路网距离。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.76** `NetworkPortalAssignCandidateItem` 增加 `distanceTier` / `distanceSummary` / `coverageMatched`；Page 增加 `workOrderRegionSummary` |
| ReadModel | 工单省市区 + 网点 `ServiceCoverage` 命中档位；师傅当前开放任务区域细化；行政区目录显示名 |
| Network Web | 工作台/工作区分配抽屉展示距离摘要，移除“距离尚未交付”占位文案 |

距离档位：

- `SAME_DISTRICT` / `SAME_CITY` / `SAME_PROVINCE`
- `OUTSIDE_COVERAGE`（有 Coverage 但未命中）
- `UNKNOWN`（工单缺行政区或 Coverage 未配置）

## 3. 明确未实现

- 经纬度/路网米制距离、路况 ETA
- 数值推荐评分（事实型推荐解释见 M412）
- 产品负责人视觉金标（状态保持 `READY_FOR_REVIEW`）

## 4. 权限与边界

- 仍要求 `networkTask.read` + `technician.readOwnNetwork`
- Coverage 查询口径与 DISPATCH 一致：`brandCode` + `serviceProductCode`
- 不暴露客户地址 PII；仅非敏感行政区摘要
