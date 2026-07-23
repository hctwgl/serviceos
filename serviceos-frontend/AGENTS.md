# ServiceOS Frontend Agent Guide

本文件是 `serviceos-frontend` 的工程导航补充。产品语义以
`serviceos-architecture/product-design/` 为准，跨仓库硬约束和验证分级以根 `AGENTS.md` 为准。

## 当前 Workspace

这是一个 pnpm Workspace，Node 最低版本为 22.18.0。业务工程只有以下七个包：

| 路径 | 包名 | 职责 |
|---|---|---|
| `apps/admin` | `@serviceos/admin` | 平台管理端 |
| `apps/network` | `@serviceos/network` | 网点协作端 |
| `apps/technician` | `@serviceos/technician` | 师傅在线 Web 参考实现 |
| `packages/api-client` | `@serviceos/api-client` | Core API 协议适配与安全错误映射 |
| `packages/auth-context` | `@serviceos/auth-context` | OIDC PKCE、Token 会话与客户端上下文 |
| `packages/design-system` | `@serviceos/design-system` | 设计令牌、Ant Design Vue 唯一导出边界 |
| `packages/product-language` | `@serviceos/product-language` | 产品术语和展示文案映射 |

`vben/` 保存 Admin 依赖的上游 UI 基础包，不承载 ServiceOS 业务规则。不要把 `vben/` 当作产品应用，
也不要为旧 `serviceos-*-web` 或 `serviceos-web-core` 路径增加兼容包。

## 依赖边界

```text
apps/*
  -> packages/api-client
  -> packages/auth-context
  -> packages/design-system
  -> packages/product-language

packages/api-client -> packages/auth-context
```

- 应用之间不得互相导入源码；
- 共享包不得依赖任一应用，也不得包含 Portal/角色专属业务策略；
- Ant Design Vue 及其图标只通过 `@serviceos/design-system` 暴露给产品代码；
- Tenant、Project、Region、Network Scope 和 Capability 由后端权威判断，前端只消费结果；
- `serviceos-contracts/target/generated-clients` 是验证产物，不提交，也不复制进 Workspace；
- 删除或新增 Workspace 包时同步 `pnpm-lock.yaml`、本文件和仓库预检。

## 验证

统一入口：

```bash
bash scripts/agent-verify.sh frontend
```

该命令必须完成：

1. 三个应用与全部共享包的 ESLint；
2. 七个 `@serviceos/*` 包的类型检查；
3. 所有已有单元测试；
4. Admin 产品边界静态检查；
5. Admin、Network、Technician 三个生产构建。

局部开发可以从 Workspace 内运行 `corepack pnpm lint`、`typecheck`、`test:unit` 或 `build`，交付前仍以
统一入口为准。产品边界检查失败时修复真实导入、令牌或用户可见术语，不得删除门禁或放宽错误级别。
