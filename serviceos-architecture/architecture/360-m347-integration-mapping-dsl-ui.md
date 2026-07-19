---
title: M347 Admin INTEGRATION Mapping DSL 可视编辑
status: Implemented
milestone: M347
lastUpdated: 2026-07-19
relatedMilestones: [M334, M339, M315]
---

# M347 Admin INTEGRATION Mapping DSL 可视编辑

## 目标

在 Admin `PolicyAssetEditor` 的 INTEGRATION 结构化设计器中，可视编辑入站 Mapping DSL 字段与
`messageType`，与定义 JSON 双向同步，覆盖运行时已支持的 `constantValue` / `defaultValue` /
`enumMap` / `condition`。

## 范围与非目标

- 范围：
  - INBOUND `messageType`（CREATE/UPDATE/CANCEL）；切到 OUTBOUND 时清除 `messageType`
  - 每条 `fieldMappings`：`constantValue` / `defaultValue` / `enumMap`（`from=to` 多行）/`condition`
  - `constantValue` 与 `defaultValue`/`enumMap` 互斥；空值删除可选键（非整项 merge 残留）
  - condition：`PRESENT` / `EQUALS` / `NOT_EQUALS` / `IN` / `NOT_IN` + sourcePath/value/values
- 明确不做：
  - OpenAPI / Flyway 变更
  - 嵌套表达式引擎 UI（Mapping condition 保持扁平 DSL）
  - 吉利联调；OEM Mapper 拆除以外的运行时变更

## 已实现

- `PolicyAssetEditor.vue` INTEGRATION 区块：direction/messageType + Mapping DSL 控件
- Admin `npm run build` 通过

## 明确未实现

- DISPATCH scope/fallback/allocationRatio 编辑器（见后续 M348）
- Technician FORM 条件执行器（见后续 M349）
- AMOUNT/加权、Coverage CRUD、吉利联调

## 验证

```bash
cd serviceos-admin-web && npm run build
```
