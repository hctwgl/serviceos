---
title: M414 Admin 主数据治理台（车企/品牌/行政区树）
version: 0.1.0
status: Implemented
milestone: M414
lastUpdated: 2026-07-21
---

# M414 Admin 主数据治理台

## 1. 目标

关闭 Admin `UI_DATA_GAP`「完整国标行政区全量树与车企品牌树治理 UI」的主路径：提供可运营的车企启停、品牌目录与可展开行政区树，不再只依赖项目选择器只读目录。

## 2. 已实现

| 层 | 内容 |
|---|---|
| Flyway | **V143** `prj_client_brand`；34 个省级国标骨架 + 演示地市/区县扩展 |
| OpenAPI | **1.0.80**：车企 `status` 筛选；`/status`；品牌 CRUD/生命周期；`RegionCatalogItem.childCount` |
| Backend | 登记/启停车企与品牌；根级省级树 + 按 `parentCode` 懒加载；审计 + 幂等 |
| Page Registry | `ADMIN.MASTERDATA.CATALOG` → `master-data` /「主数据治理」；catalog **v22** |
| Admin Web | `/master-data` 车企与品牌 Tab + 行政区树 Tab |

## 3. 权限

- 读：`project.read`
- 写（登记/启停）：`project.create`
- 选择器默认仍只消费 `ACTIVE` 车企（`status` 缺省）

## 4. 明确未实现

- 全国区县级全量国标树（本切片为省级骨架 + 演示下级）
- 拼音索引检索
- 多级子品牌树
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
