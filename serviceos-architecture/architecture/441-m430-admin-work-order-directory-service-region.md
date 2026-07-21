---
title: M430 Admin 工单目录服务区域列
version: 0.1.0
status: Implemented
milestone: M430
lastUpdated: 2026-07-21
relatedMilestones: [M68, M373, M429]
openapiVersion: "1.0.93"
---

# M430 Admin 工单目录服务区域列

## 1. 目标

关闭 Admin `ADMIN.WORKORDER.LIST` UI_DATA_GAP「服务区域」列：展示工单目录既有 `provinceCode` / `cityCode` / `districtCode`。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | 无 bump（仍 **1.0.93**）；`WorkOrder` 区域字段早已 required |
| Admin Web | 工单中心「服务区域」列以 `省/市/区` 国标码拼接展示 |
| 证据 | Playwright 视觉基线 |

## 3. 边界

- 不伪造区域名称；不新增 region-catalog fan-in（名称解析可后续增强）
- 不交付按区域筛选 API；更多筛选仍诚实标注缺口
- 无 Flyway、无新 capability

## 4. 明确未实现

- 行政区中文名解析 / 拼音检索
- 阶段、责任人、SLA、独立 updatedAt、列表 total
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
