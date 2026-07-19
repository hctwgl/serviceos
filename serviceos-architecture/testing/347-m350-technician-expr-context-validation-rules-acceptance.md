---
title: M350 Technician 表达式上下文与 validationRules 验收矩阵
status: Implemented
milestone: M350
lastUpdated: 2026-07-19
---

# M350 Technician 表达式上下文与 validationRules 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M350-01 | 详情含六项非 PII 表达式头 | 与 wo_work_order 一致 | `TechnicianPortalFeedPostgresIT#taskDetail…` |
| M350-02 | 缺工单 | RESOURCE_NOT_FOUND | `DefaultTechnicianPortalQueryService` |
| M350-03 | H5 workOrder/region 求值 | 可用权威头 | `serviceosExprV1Evaluate.test.mjs` |
| M350-04 | validationRules assert 失败 | 阻断提交并展示 | `TechnicianPortalTaskDetailPage.vue` |
| M350-05 | OpenAPI | 1.0.44 | `serviceos-core-v1.yaml` |
| M350-06 | Technician build | 通过 | `npm run build` |

## 明确不验收

- editableWhen/defaultExpression、iOS 共用执行器、吉利联调
