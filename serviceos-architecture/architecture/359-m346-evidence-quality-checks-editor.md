---
title: M346 EVIDENCE qualityChecks 可视编辑
status: Implemented
milestone: M346
lastUpdated: 2026-07-19
relatedMilestones: [M313, M341]
---

# M346 EVIDENCE qualityChecks 可视编辑

## 目标

在 EVIDENCE 结构化配置器中编辑资料项 `qualityChecks` 的 `checkType` / `severity`，与定义 JSON 双向同步。

## 范围与非目标

- 范围：增删改 checkType（schema 枚举）与 severity（WARN/BLOCK）；空列表清除字段
- 明确不做：parameters/expression 编辑；OCR/CV 运行时启用；OpenAPI/Flyway

## 验证

```bash
cd serviceos-admin-web && npm run build
```
