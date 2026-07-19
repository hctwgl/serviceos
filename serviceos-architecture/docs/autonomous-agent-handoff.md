---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR #148～#167：M321～M340 Draft stacked
- 本切片：**M341** EVIDENCE requiredWhen ConditionBuilder（Draft，base=#167）
- `master`：`32b902f897d19d2c906acac899990bf1aa2bb056`
- latestMilestone：**M341**
- Flyway：**127**；OpenAPI：**1.0.43**（无本里程碑变更）
- `baselineCommit`：功能提交后回填

## 本回合完成

### M341 EVIDENCE requiredWhen 条件积木嵌入

- `StructuredAssetEditor` EVIDENCE 项嵌入 `ConditionBuilder` 编辑 `requiredWhen`
- 空源码清除可选表达式；上下文路径积木；formValues 高级源码
- 文档：`354-m341-*` / `338-m341-*`

## 验证

```text
node serviceos-admin-web/src/expression/serviceosExprV1Blocks.test.mjs
cd serviceos-admin-web && npm run build
```

## 下一本地主线

1. 嵌套 SERVICEOS_EXPR_V1 round-trip / 递归条件组（M342）
2. 吉利联调 — `BLOCKED_EXTERNAL`

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight
