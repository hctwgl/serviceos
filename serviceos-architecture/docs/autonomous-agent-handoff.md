---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR #148～#168：M321～M341 Draft stacked
- 本切片：**M342** 嵌套条件组 round-trip（Draft，base=#168）
- `master`：`32b902f897d19d2c906acac899990bf1aa2bb056`
- latestMilestone：**M342**
- Flyway：**127**；OpenAPI：**1.0.43**（无本里程碑变更）
- `baselineCommit`：功能提交后回填

## 本回合完成

### M342 嵌套条件组 round-trip

- ExprParser 递归下降：括号 / && / ||；formValues 方括号不干扰
- `ConditionGroupBlock` 递归 UI；一元 `!` 仍高级源码
- 文档：`355-m342-*` / `339-m342-*`

## 验证

```text
node serviceos-admin-web/src/expression/serviceosExprV1Blocks.test.mjs
cd serviceos-admin-web && npm run build
```

## 下一本地主线

1. REFERENCE_OEM Update/Cancel 或 AMOUNT/加权比例
2. 吉利联调 — `BLOCKED_EXTERNAL`

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight
