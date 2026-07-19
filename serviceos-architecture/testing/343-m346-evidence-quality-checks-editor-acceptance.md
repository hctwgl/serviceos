---
title: M346 EVIDENCE qualityChecks 可视编辑 验收矩阵
status: Implemented
milestone: M346
lastUpdated: 2026-07-19
---

# M346 EVIDENCE qualityChecks 可视编辑 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M346-01 | 添加/编辑/删除 qualityCheck | JSON 同步 checkType/severity | `StructuredAssetEditor.vue` |
| M346-02 | 删空 | 清除 qualityChecks 字段 | 同上 |
| M346-03 | Admin build | 通过 | `npm run build` |

## 明确不验收

- parameters/expression、OCR/CV 运行时、吉利联调
