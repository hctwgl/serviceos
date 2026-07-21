---
title: M406 车企主数据与行政区名称目录
version: 0.1.0
status: Implemented
milestone: M406
lastUpdated: 2026-07-20
---

# M406 车企主数据与行政区名称目录

## 1. 目标

关闭项目选择器剩余 UI_DATA_GAP：提供正式车企显示名与行政区名称目录，前端不再靠前端硬编码猜测。

## 2. 已实现

| 层 | 内容 |
|---|---|
| Flyway | **V142** `prj_client_directory` + `prj_region_catalog`（含演示/测试常用区域种子） |
| OpenAPI | **1.0.72** `/project-clients`、`/region-catalog`；reference-options 增加显示名 |
| Backend | 登记车企、列表目录；创建项目时 ensureClient；reference-options 附带名称 |
| Admin | `ProjectClientPicker` / `ProjectRegionPicker` 消费正式目录 |

## 3. 明确未实现

- 完整国标行政区全量树与拼音索引（省级骨架与治理台已由 M414 推进；全国区县全量仍未交付）
- 车企品牌/子品牌树与生命周期治理 UI（品牌一级治理已由 M414 关闭；多级子品牌仍未交付）
- 产品负责人视觉金标

## 4. 权限

- 读：`project.read`
- 写车企：`project.create`
