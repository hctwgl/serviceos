---
title: M34 不可变表单提交运行时验收
version: 0.1.0
status: Implemented
---

# M34 不可变表单提交运行时验收

| ID | 优先级 | 验收点 | 自动化证据 |
|---|---|---|---|
| M34-FORM-001 | P0 | 只向 Task 冻结 Bundle 的精确 FormVersion 提交 | `FormSubmissionPostgresIT` |
| M34-FORM-002 | P0 | 当前责任人、RUNNING HUMAN、Project Scope 与 guard 门禁 | `FormSubmissionPostgresIT` |
| M34-FORM-003 | P0 | Submission/Validation 不可变、版本单调 | `FormSubmissionPostgresIT`、V036 |
| M34-FORM-004 | P0 | required、类型、未知字段和永久文件 URL 服务端校验 | `FormSubmissionPostgresIT` |
| M34-TX-001 | P0 | 提交、验证、审计、Outbox、幂等结果同事务且重放冻结 | `FormSubmissionPostgresIT` |
| M34-FAIL-001 | P0 | 表达式/validator/prefill 未获批时 422 且无污染 | `FormSubmissionPostgresIT` |
| M34-API-001 | P1 | 匿名拒绝、JWT 身份透传、values 保持 JSON 对象 | `FormSubmissionControllerSecurityTest`、OpenAPI 0.10.0 |
| M34-EVT-001 | P1 | `form.submitted@v1` 不复制原始敏感值 | Event Schema Governance |
| M34-DEP-001 | P0 | staging 发布门禁校验 `036/38` | staging rehearsal |

本矩阵证明 M34 已实现边界，不替代 M3 FORM-001～FORM-005。表达式、草稿、预填冲突、更正链与
Task 完成引用仍必须由后续里程碑验收。
