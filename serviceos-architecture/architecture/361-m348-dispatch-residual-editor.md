---
title: M348 DISPATCH 残留结构化编辑器
status: Implemented
milestone: M348
lastUpdated: 2026-07-19
relatedMilestones: [M337, M338, M315, M347]
---

# M348 DISPATCH 残留结构化编辑器

## 目标

在 Admin `PolicyAssetEditor` 的 DISPATCH 设计器中补齐 schema 必需残留字段：
`scope` / `fallback` / `allocationRatio`，并校正硬过滤 `order` 与评分 `factorKey` 枚举。

## 范围与非目标

- 范围：
  - `scope.brandCodes|businessTypes|regionCodes`（逗号分隔，至少一项）
  - `fallback.onNoCandidate|manualRole|resolutionHours|preWarningMinutes`
  - `allocationRatio.enabled`；`period`/`measure` 锁定为 `MONTH`/`ORDER_COUNT`
  - 硬过滤 `filterKey` 枚举 + `order`；评分 `factorKey` 枚举 + ConditionBuilder 表达式
- 明确不做：
  - AMOUNT / WEIGHTED_VOLUME 选项（未业务确认）
  - `qualityMayOverride` 履约质量覆盖 UI
  - Coverage / allocation target CRUD OpenAPI
  - OpenAPI / Flyway 变更

## 已实现

- `PolicyAssetEditor.vue` DISPATCH 区块残留控件
- Admin `npm run build` 通过

## 明确未实现

- Technician FORM 条件执行器（M349）
- AMOUNT/加权、Coverage 维护 UI、吉利联调

## 验证

```bash
cd serviceos-admin-web && npm run build
```
