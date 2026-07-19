---
title: M344 EVIDENCE 同 stage FORM fieldKey 发现 验收矩阵
status: Implemented
milestone: M344
lastUpdated: 2026-07-19
---

# M344 EVIDENCE 同 stage FORM fieldKey 发现 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M344-01 | 提取 FORM fieldKey | stage + keys | `extractFormFieldKeys.test.mjs` |
| M344-02 | 同 stage 发现 / 异 stage 忽略 / DISCARDED 忽略 | 仅命中草稿 | 同上 |
| M344-03 | EVIDENCE ConditionBuilder 传入 keys | formValues 可选 | `StructuredAssetEditor` + `ConfigurationDesignerPage` |
| M344-04 | Admin build | 通过 | `npm run build` |

## 明确不验收

- Bundle 运行时解析、OpenAPI、吉利联调
