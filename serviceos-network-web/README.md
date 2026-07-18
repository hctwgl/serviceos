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

容器必须从仓库根目录构建，以便安装同仓 `@serviceos/web-core`：

```bash
docker build -f serviceos-network-web/Dockerfile \
  --build-arg VITE_SERVICEOS_CLIENT_VERSION=1.0.0 \
  --build-arg VITE_OIDC_ENABLED=true \
  --build-arg VITE_OIDC_ISSUER=https://identity.example/realms/serviceos \
  --build-arg VITE_OIDC_CLIENT_ID=serviceos-network-web \
  -t serviceos-network-web:1.0.0 .
```

运行时用 `SERVICEOS_API_UPSTREAM` 指定后端反代目标；容器监听 8080 并提供 `/healthz`。构建参数不含
client secret，OIDC 公共客户端仍使用 PKCE。
