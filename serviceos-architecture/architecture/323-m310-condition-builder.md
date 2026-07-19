---
title: M310 SERVICEOS_EXPR_V1 条件积木
status: Implemented
milestone: M310
lastUpdated: 2026-07-19
relatedMilestones: [M287, M289, M294, M295, M309]
---

# M310 SERVICEOS_EXPR_V1 条件积木

## 目标

为配置设计器提供可视化条件积木：字段/运算符/值/AND-OR → 生成白名单 `SERVICEOS_EXPR_V1` 源码；保留高级源码模式；双向同步定义 JSON。

## 范围

- `serviceosExprV1Blocks.ts`：编译/尽力解析/白名单（对齐后端 `ServiceOsExprV1Evaluator`）
- `ConditionBuilder.vue`：积木 UI + 高级源码切换 + 预览
- `WorkflowCanvas` 排他网关边条件接入积木
- `ConfigurationDesignerPage` 对 RULE/DISPATCH/NOTIFICATION/ASSIGNEE_POLICY/PRICING 首条 `when`/`expression` 双向编辑
- Node 冒烟测试 + Playwright `M310 条件积木…`
- `npm run build`（vue-tsc）通过

## 明确未实现

- 嵌套括号混合组的完整 round-trip 解析；FORM 字段 `formValues["…"]` 积木；全资产专用属性面板深化；吉利 OEM

## 验证

```bash
node serviceos-admin-web/src/expression/serviceosExprV1Blocks.test.mjs
cd serviceos-admin-web && npm run build
# 可选（需本地 Keycloak）：npx playwright test admin-configuration-designer
```
