---
title: M414 Admin 主数据治理台验收矩阵
version: 0.1.0
status: Implemented
milestone: M414
lastUpdated: 2026-07-21
---

# M414 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 省级骨架 | 根级含 110000/440000/810000 等；childCount≥0 | `ProjectQueryPostgresIT` |
| A2 | 懒加载下级 | `parentCode=440300` 返回南山/福田等区 | 同上 |
| A3 | 车企启停 | DISABLED 后默认 ACTIVE 列表不可见；ALL 可见 | 同上 |
| A4 | 品牌生命周期 | 登记/停用品牌；ACTIVE 过滤生效 | 同上 |
| A5 | 导航 | ADMIN.MASTERDATA.CATALOG → /master-data；catalog v22 | `CodePageRegistry` + `PortalContextPostgresIT` |
| A6 | Admin UI | 车企表、品牌面板、行政区树可见 | Playwright `admin-master-data-catalog-product.spec.ts` |
| A7 | 模块边界 | ArchitectureTest | ArchitectureTest |

产品状态：`READY_FOR_REVIEW`。
