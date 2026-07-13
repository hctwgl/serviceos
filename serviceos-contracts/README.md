# ServiceOS Contracts

本目录保存实现、Portal、连接器和事件消费者共同遵守的机器可读契约。

- `src/main/resources/openapi/`：HTTP OpenAPI 3.1；
- `src/main/resources/events/`：版本化事件 JSON Schema；
- `ContractValidationTest`：解析 OpenAPI，并用正式 Schema 校验事件样本。

当前文件事件包含 `file.scan-completed.v1`；事件只发布 fileId、摘要、检测 MIME、生命周期和 scanner 版本，不发布 object key 或短期 URL。

契约变更必须先更新 Schema 和样本，再修改实现。删除必需字段、改变字段类型或复用同一事件版本表达新语义属于破坏性变更，应发布新版本。
