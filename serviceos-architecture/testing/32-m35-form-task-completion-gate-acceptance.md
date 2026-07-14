---
title: M35 表单任务完成引用门禁验收
version: 0.1.0
status: Implemented
---

# M35 表单任务完成引用门禁验收

| 场景 ID | 优先级 | 验收目标 | 自动化证据 |
|---|---:|---|---|
| M35-FORM-001 | P0 | 表单 Task 只接受同 Task/Project/formKey/冻结 FormVersion 的 VALIDATED submission | `FormSubmissionPostgresIT` |
| M35-FORM-002 | P0 | `resultRef` 使用权威 submission UUID，`resultDigest` 精确匹配 contentDigest | `FormSubmissionPostgresIT`、OpenAPI 0.11.0 |
| M35-FORM-003 | P0 | INVALID、跨 Task 和错误摘要拒绝且无 Task、幂等、审计或 Outbox 污染 | `FormSubmissionPostgresIT` |
| M35-BOUNDARY-001 | P0 | task 通过公开扩展端口调用所属模块校验，不反向依赖 forms | `ArchitectureTest` |
| M35-REGRESSION-001 | P0 | 非表单 HUMAN Task 的完成、重放和事务回滚语义不变 | `HumanTaskCommandPostgresIT` |

本矩阵只证明 Task 对 M34 权威提交的完成引用门禁，不等同 FORM-001～FORM-005、Evidence 或
Review 闭环完成。
