---
title: M344 EVIDENCE 同 stage FORM fieldKey 发现
status: Implemented
milestone: M344
lastUpdated: 2026-07-19
relatedMilestones: [M340, M341]
---

# M344 EVIDENCE 同 stage FORM fieldKey 发现

## 目标

EVIDENCE 设计器编辑 `requiredWhen` 时，自动发现同 stage FORM 草稿的 `fieldKey`，
供 `formValues["…"]` 积木下拉，无需仅靠高级源码。

## 范围与非目标

- 范围：`extractFormFieldKeys` / `discoverFormFieldKeysForStage`；设计器加载 FORM 草稿；
  按 `stage` 匹配合并 fieldKey；提示来源 assetKey
- 明确不做：已发布 Bundle 运行时解析、formRef 图、OpenAPI/Flyway、Technician 执行器

## 验证

```bash
node serviceos-admin-web/src/expression/extractFormFieldKeys.test.mjs
cd serviceos-admin-web && npm run build
```
