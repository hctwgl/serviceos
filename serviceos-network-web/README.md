# ServiceOS Network Web

独立网点协作 Web 应用。当前包含 OIDC Authorization Code + PKCE、`/me` Network Context、
Capability、服务端导航，以及原 Admin 内嵌 Network Portal 的正式业务页面。

```bash
npm ci
npm run build
```

本地开发可复制 `.env.development.example` 为 `.env.development.local`。生产 API 地址及 OIDC issuer
必须使用同源路径/HTTPS；Access Token 只保存在内存，刷新页面需要重新登录；`clientKind` 固定为
`NETWORK_WEB`，不能由环境变量改写，也不参与授权。
