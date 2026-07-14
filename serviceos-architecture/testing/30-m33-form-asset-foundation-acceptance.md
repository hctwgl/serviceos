---
title: M33 表单资产发布基础验收
version: 0.1.0
status: Implemented
---

# M33 表单资产发布基础验收

| ID | 优先级 | 验收点 | 自动化证据 |
|---|---|---|---|
| M33-CFG-001 | P0 | 合法 FORM 按精确版本发布 | `ConfigurationPublicationPostgresIT` |
| M33-CFG-002 | P0 | 非法 Schema、未知版本、身份不一致在落库前拒绝 | `ConfigurationPublicationPostgresIT` |
| M33-CFG-003 | P0 | 勘测与安装 FormVersion 同 Bundle 锁定 | `ConfigurationPublicationPostgresIT` |
| M33-CFG-004 | P0 | 多版本误用单例读取时失败关闭 | `ConfigurationPublicationPostgresIT` |
| M33-CFG-005 | P1 | 架构 Schema 与运行时资源无漂移 | `ConfigurationSchemaDriftTest` |
| M33-TASK-001 | P0 | Workflow 首任务和后续任务均冻结节点 `formRef` | `WorkflowDefinitionParserTest`、`WorkflowBootstrapPostgresIT`、`WorkflowLinearProgressionPostgresIT` |
| M33-TASK-002 | P0 | Task 公开上下文按租户读取已冻结 `formRef` | `HumanTaskCommandPostgresIT` |
| M33-FORM-001 | P0 | Task 通过冻结 Bundle 摘要精确解析 FormVersion，不读取最新配置 | `WorkflowBootstrapPostgresIT`、V035 |
| M33-FORM-002 | P0 | 表单查询要求 `form.read` 和 Task Project Scope | `WorkflowBootstrapPostgresIT` |
| M33-FORM-003 | P1 | HTTP 拒绝匿名请求且定义保持 JSON 对象 | `TaskFormControllerSecurityTest`、OpenAPI 0.9.0 |

本矩阵不替代 M3 FORM-001～FORM-005；M34 只补齐不依赖表达式的 FormSubmission 内核，
完整表达式、草稿、预填冲突、更正和任务完成引用后才能关闭这些场景。
