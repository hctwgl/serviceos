---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR #148～#166：M321～M339 Draft stacked
- PR #167：https://github.com/hctwgl/serviceos/pull/167 — **M340** FORM ConditionBuilder（Draft，base=#166）
- `master`：`32b902f897d19d2c906acac899990bf1aa2bb056`
- latestMilestone：**M340**
- Flyway：**127**；OpenAPI：**1.0.43**（无本里程碑变更）
- `baselineCommit`：`038e15b6`（功能证据）

## 本回合完成

### M340 FORM ConditionBuilder 条件积木嵌入

- `formValues["fieldKey"]` 积木编译/解析；布尔/数值字面量
- `ConditionBuilder` 支持 `formFieldKeys`；FORM `StructuredAssetEditor` 嵌入
  section `visibility`、field `visibleWhen` / `requiredWhen`
- 空源码清除可选表达式；Node 冒烟 + Admin `npm run build`
- 文档：`353-m340-*` / `337-m340-*`

## 验证

```text
node serviceos-admin-web/src/expression/serviceosExprV1Blocks.test.mjs
cd serviceos-admin-web && npm run build
```

## 下一本地主线

1. 嵌套 SERVICEOS_EXPR_V1 round-trip / 递归条件组，或 EVIDENCE requiredWhen 积木
2. 吉利联调 — `BLOCKED_EXTERNAL`

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight
