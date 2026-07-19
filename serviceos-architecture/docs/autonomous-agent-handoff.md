---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR #148～#170：M321～M343 Draft stacked
- 本切片：**M344** EVIDENCE FORM fieldKey 发现（Draft，base=#170）
- latestMilestone：**M344**
- Flyway：**127**；OpenAPI：**1.0.43**

## 验证

```text
node serviceos-admin-web/src/expression/extractFormFieldKeys.test.mjs
cd serviceos-admin-web && npm run build
```

## 下一

1. M345 一元 `!` 积木
2. M346 EVIDENCE qualityChecks 可视编辑
3. AMOUNT（待业务确认）/ BUSINESS SLA / 吉利 BLOCKED_EXTERNAL
