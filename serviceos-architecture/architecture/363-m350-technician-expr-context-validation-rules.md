---
title: M350 Technician 表达式上下文与 validationRules
status: Implemented
milestone: M350
lastUpdated: 2026-07-19
relatedMilestones: [M349, M243, M246]
---

# M350 Technician 表达式上下文与 validationRules

## 目标

Technician Portal 任务详情暴露与服务端 `FormValueValidator` 同源的非 PII 工单/区域头，
使 H5 可对 `workOrder.*` / `region.*` 与跨字段 `validationRules` 执行 SERVICEOS_EXPR_V1。

## 范围与非目标

- 范围：
  - OpenAPI `1.0.44`：`TechnicianPortalTaskDetail` 增加
    `clientCode`/`brandCode`/`serviceProductCode`/`provinceCode`/`cityCode`/`districtCode`
  - `DefaultTechnicianPortalQueryService` 经 `WorkOrderExpressionContextQuery` 读取；缺工单失败关闭
  - H5：路径注入 + `validationRules[].assert` 预检（失败关闭阻断提交）
- 明确不做：
  - `editableWhen` / `defaultExpression`（发布与提交运行时仍未接受）
  - iOS 共用执行器
  - 地址正文 / 联系人 / PII

## 验证

```bash
bash scripts/agent-verify.sh test TechnicianPortalControllerSecurityTest
bash scripts/agent-verify.sh it TechnicianPortalFeedPostgresIT
node --experimental-strip-types serviceos-technician-web/src/expression/serviceosExprV1Evaluate.test.mjs
cd serviceos-technician-web && npm run build
```
