# ServiceOS Contracts

本目录保存实现、Portal、连接器和事件消费者共同遵守的机器可读契约。

- `src/main/resources/openapi/`：HTTP OpenAPI 3.1；
- `src/main/resources/events/`：版本化事件 JSON Schema；
- `ContractValidationTest`：解析 OpenAPI，并用正式 Schema 校验事件样本；
- `EventSchemaGovernanceTest`：校验事件文件名、版本元数据、唯一身份和同名有效样本；
- `GeneratedClientContractTest`：证明 Maven 生命周期产出固定版本的 TypeScript Fetch 客户端；
- `scripts/check-contract-compatibility.sh`：相对不可变 Git ref 阻断 OpenAPI 破坏和已发布事件版本修改；
- `scripts/verify-client-generation-reproducibility.sh`：验证清理重生成后的完整文件树摘要不漂移。

当前文件事件包含 `file.scan-completed.v1`；事件只发布 fileId、摘要、检测 MIME、生命周期和 scanner 版本，不发布 object key 或短期 URL。

契约变更必须先更新 Schema 和样本，再修改实现。删除必需字段、改变字段类型或复用同一事件版本表达新语义属于破坏性变更。已发布事件版本禁止原地修改，必须新增 `-vN.schema.json`。

本地验证：

```bash
OASDIFF_BIN="$(scripts/install-oasdiff.sh target/contract-tools)" \
  scripts/check-contract-compatibility.sh origin/master

../mvnw --batch-mode --no-transfer-progress -pl serviceos-contracts -am clean verify

scripts/verify-client-generation-reproducibility.sh
```

生成客户端位于 `target/generated-clients/typescript-fetch`，来源清单位于 `target/client-artifacts/typescript-fetch`。两者都是构建产物，不提交 Git；CI 会保留带 commit SHA 名称的 artifact 14 天。
