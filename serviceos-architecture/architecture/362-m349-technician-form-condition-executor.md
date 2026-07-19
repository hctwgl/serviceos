---
title: M349 Technician Web FORM 条件执行器
status: Implemented
milestone: M349
lastUpdated: 2026-07-19
relatedMilestones: [M340, M342, M345, M263]
---

# M349 Technician Web FORM 条件执行器

## 目标

Technician H5 对冻结表单的 `section.visibility` / `field.visibleWhen` / `field.requiredWhen`
执行 SERVICEOS_EXPR_V1 布尔子集，取消此前“存在条件即 fail-close 整表”的硬阻断。

## 范围与非目标

- 范围：
  - `serviceosExprV1Evaluate`：==/!=/&&/||/!/()、白名单路径、`formValues["…"]`
  - 可用上下文：草稿 `formValues` + `task.stageCode` / `task.taskType`
  - 显隐/必填动态 UI；隐藏字段不提交；求值失败失败关闭并阻断提交
  - 引用 `workOrder.*` / `region.*` 的条件仍标为不支持（Portal 详情无权威值）
- 明确不做：
  - `editableWhen` / `defaultExpression` / 跨字段 `validationRules`
  - iOS 共用执行器 / 共享 npm 包抽取
  - OpenAPI / Flyway 变更

## 已实现

- `serviceos-technician-web/src/expression/serviceosExprV1Evaluate.ts` + Node 冒烟
- `TechnicianPortalTaskDetailPage.vue` 条件驱动显隐/必填
- `npm run build` 通过

## 明确未实现

- workOrder/region 权威上下文注入、iOS 执行器、editableWhen/默认值、AMOUNT/加权、吉利联调

## 验证

```bash
node --experimental-strip-types serviceos-technician-web/src/expression/serviceosExprV1Evaluate.test.mjs
bash scripts/verify-web-core.sh
cd serviceos-technician-web && npm run build
```
