---
title: M246 Technician Portal 表单提交安全摘要验收矩阵
status: Implemented
milestone: M246
lastUpdated: 2026-07-18
---

# M246 Technician Portal 表单提交安全摘要验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M246-01 | 当前责任 + project `form.read` | 返回安全摘要集合 | PostgreSQL IT |
| M246-02 | 缺 `form.read` | `formSubmissions=null`，任务/Visit 仍返回 | PostgreSQL IT + MVC |
| M246-03 | 表单摘要 | 仅 ID/key/version/status/count/time，无 values/message/digest/submittedBy | DTO + OpenAPI 1.0.20 |
| M246-04 | 页面有权 | 展示表单键、版本和校验状态 | Admin E2E |
| M246-05 | 模块/迁移 | readmodel 复用 forms API；Flyway 100/102 | ArchitectureTest + preflight |

## 明确未验收

表单写、values/schema、Evidence、整改、离线工作包与通知。
