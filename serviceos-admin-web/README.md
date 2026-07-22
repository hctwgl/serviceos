# ServiceOS 旧 Admin Web（已冻结）

该工程只保留到新 Admin 黄金链路完成人工验收并原子切换。禁止继续新增页面、路由和产品功能；
新的 Web 产品实现位于 `serviceos-frontend/apps/admin`。旧工程不再维护 Playwright、视觉截图或浏览器 E2E。

```bash
npm ci
npm run dev
npm run build
```

`npm run dev` 与 `npm run build` 都会先依据当前 Core OpenAPI 生成并编译
`@serviceos/core-client`。生成物位于 `serviceos-contracts/target/generated-clients`，属于可复现的
本地构建产物，不提交 Git；不要跳过 npm 脚本直接执行 `vite`，否则全量验证清理 `target` 后会留下
无法解析的本地依赖链接。

## 本地 OIDC

复制 `.env.development.example` 为 `.env.development.local`，启动
`serviceos-deploy/compose.yaml` 中的 PostgreSQL/Keycloak 与后端后，可使用
`developer / local-dev-change-me` 走 Authorization Code + PKCE。该适配器仅在显式开启的
Vite 开发模式可用，生产构建失败关闭，不提供手工粘贴 JWT 的后门。

旧 Admin 只允许执行 `npm run build` 和保留的快速单元测试。业务规则验证已归属后端精准测试；
产品验收统一使用新 Workspace、统一场景数据和真实 Chrome 人工操作。
