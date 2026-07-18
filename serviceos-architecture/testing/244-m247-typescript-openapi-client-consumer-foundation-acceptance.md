---
title: M247 TypeScript OpenAPI Client 消费基础验收矩阵
status: Implemented
milestone: M247
lastUpdated: 2026-07-18
---

# M247 TypeScript OpenAPI Client 消费基础验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M247-01 | 两次干净生成 | 完整生成文件树摘要一致 | `verify-client-generation-reproducibility.sh` |
| M247-02 | 仓库 TypeScript 6 编译 | CommonJS 与 ESM 均成功产生声明和 JS | `verify-typescript-client-consumer.sh` |
| M247-03 | 独立 npm 消费者 | tarball 可安装，`DefaultApi` 类型可导入 | 临时 consumer + `tsc --noEmit --strict` |
| M247-04 | 运行时入口 | 包入口可加载并实例化 `Configuration`/`DefaultApi` | Node smoke |
| M247-05 | 契约边界 | Core OpenAPI/Flyway 不变，无 Portal 角色假设 | contract gate + diff review |

## 明确未验收

Swift Client、远端制品发布、Web/iOS 基础设施、独立 Portal、兼容元数据与移动端设备验证。
