---
title: M268 配置治理 MVP（Workflow 条件与依赖静态校验）
status: Implemented
milestone: M268
lastUpdated: 2026-07-18
relatedMilestones: [M16, M33, M52, M61, M267]
---

# M268 配置治理 MVP（Workflow 条件与依赖静态校验）

## 目标

加厚配置发布门禁：Workflow `transition.condition` 与 FORM/EVIDENCE 一样使用 `SERVICEOS_EXPR_V1` 对象；`EXCLUSIVE_GATEWAY` 出边在发布期静态校验；未知/非法表达式失败关闭。运行时仍不执行网关（留给后续里程碑）。

## 范围与非目标

- 范围：
  - `workflow.schema.json` condition 对齐 expression 对象；
  - 运行时内嵌 schema 漂移门禁；
  - `ConfigurationAssetSchemaValidator` 对 WORKFLOW 语义校验与 Bundle 期网关校验；
  - `WorkflowDefinitionParser` 识别对象条件（非空即条件边，线性推进仍要求无条件）。
- 明确不做：草稿/审批 UI、灰度、回放、EXCLUSIVE_GATEWAY 运行时、PARALLEL/WAIT_EVENT；完整 Workflow JSON Schema 结构门禁（历史夹具尚未对齐）暂不启用。

## 已实现

- 架构与内嵌 `workflow-v1.schema.json`：`condition` → `{language,source}`；
- WORKFLOW 发布语义校验：未知节点引用、字符串 condition、EXCLUSIVE_GATEWAY 出边数量与条件；
- Bundle 组装期复用同一语义校验；
- Parser 将对象条件视为条件边，线性推进继续失败关闭。

## 明确未实现

网关运行时求值、默认边语义、PARALLEL/WAIT_EVENT、配置审批流、完整 Workflow JSON Schema 强制校验。

## 工程证据

- `ConfigurationSchemaDriftTest`、`ConfigurationAssetSchemaValidatorTest`、`WorkflowDefinitionParserTest`
- `ConfigurationPublicationPostgresIT`、`WorkflowBootstrapPostgresIT`、`WorkflowLinearProgressionPostgresIT`
- 无 OpenAPI/Flyway 变更

## 验证命令

```bash
bash scripts/agent-verify.sh test ConfigurationSchemaDriftTest,ConfigurationAssetSchemaValidatorTest,WorkflowDefinitionParserTest
bash scripts/agent-verify.sh it ConfigurationPublicationPostgresIT,WorkflowBootstrapPostgresIT,WorkflowLinearProgressionPostgresIT
bash scripts/agent-verify.sh docs
```
