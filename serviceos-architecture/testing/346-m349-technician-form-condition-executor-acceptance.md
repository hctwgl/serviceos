---
title: M349 Technician Web FORM 条件执行器 验收矩阵
status: Implemented
milestone: M349
lastUpdated: 2026-07-19
---

# M349 Technician Web FORM 条件执行器 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M349-01 | formValues + &&/||/!/() 求值 | 布尔结果正确 | `serviceosExprV1Evaluate.test.mjs` |
| M349-02 | task.stageCode/taskType 路径 | 可求值 | 同上 + `taskExprPaths` |
| M349-03 | workOrder/region 路径 | 不支持整表（缺权威值） | `unsupportedFormReasons` |
| M349-04 | visibleWhen 为 false | 字段隐藏且不提交 | `TechnicianPortalTaskDetailPage.vue` |
| M349-05 | requiredWhen 为 true | 显示必填星号并校验 | 同上 |
| M349-06 | 求值失败 | 失败关闭并阻断提交 | `formConditionState.errors` |
| M349-07 | Technician build | 通过 | `npm run build` |

## 明确不验收

- editableWhen/默认值、iOS 共用执行器、workOrder/region 注入、吉利联调
