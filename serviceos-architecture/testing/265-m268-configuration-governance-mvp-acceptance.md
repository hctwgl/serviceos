---
title: M268 配置治理 MVP 验收矩阵
status: Implemented
milestone: M268
---

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M268-01 | condition 形状 | workflow schema 使用 `{language,source}`；漂移检查通过 | `ConfigurationSchemaDriftTest` |
| M268-02 | 线性无条件流程 | 既有 WORKFLOW 发布与推进不受影响 | Configuration/Workflow IT |
| M268-03 | EXCLUSIVE_GATEWAY 合法出边 | 每条出边表达式静态通过则可发布 | `ConfigurationAssetSchemaValidatorTest` |
| M268-04 | 网关缺条件 | 发布失败关闭 | `ConfigurationAssetSchemaValidatorTest` |
| M268-05 | 非法/字符串 condition | 发布失败关闭 | `ConfigurationAssetSchemaValidatorTest` |
| M268-06 | 线性运行时遇条件边 | Parser 仍失败关闭 | `WorkflowDefinitionParserTest` |
