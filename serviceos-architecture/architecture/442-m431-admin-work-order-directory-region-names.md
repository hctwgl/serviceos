---
title: M431 Admin 工单目录服务区域中文名
version: 0.1.0
status: Implemented
milestone: M431
lastUpdated: 2026-07-21
relatedMilestones: [M406, M414, M430]
openapiVersion: "1.0.93"
---

# M431 Admin 工单目录服务区域中文名

## 1. 目标

关闭 M430 CONTENT_GAP「行政区中文名解析」：工单目录「服务区域」列经既有 `/region-catalog` 将国标码解析为中文名。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | 无 bump（仍 **1.0.93**） |
| Admin Web | 加载 `listRegionCatalog(parentCode=*)` 建码→名映射；列优先中文名，未命中回退国标码；Tooltip 保留编码 |
| 证据 | Playwright |

## 3. 边界

- 仅消费常用目录（M406/M414 种子）；不全量国标树
- 缺 `project.read` 或目录失败时不阻断工单列表（回退码展示）
- 不交付按区域筛选、拼音索引

## 4. 明确未实现

- 全国区县全量树 / 拼音索引
- 阶段、责任人、SLA、独立 updatedAt
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
