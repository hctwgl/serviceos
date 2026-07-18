# ServiceOS Network Web

独立网点协作 Web 应用。M255 只建立 AppShell、独立依赖锁、构建和环境失败关闭边界；尚未接入 OIDC、
`/me`、Network Context、Capability、导航或任何业务页面。

```bash
npm ci
npm run build
```

本地开发可复制 `.env.development.example` 为 `.env.development.local`。生产 API 地址必须使用同源路径或 HTTPS；
`clientKind` 固定为 `NETWORK_WEB`，不能由环境变量改写，也不参与授权。
