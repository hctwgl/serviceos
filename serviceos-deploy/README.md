# ServiceOS Local Infrastructure

本地环境包含 PostgreSQL 18 与 Keycloak 26.7.0。Keycloak realm、用户和密码仅用于开发机，不得复制到测试或生产环境。

PostgreSQL 数据卷挂载到 `/var/lib/postgresql`，与 18 及更高版本官方镜像的版本化 `PGDATA` 约定一致；不要改回 17 及更早版本使用的 `/var/lib/postgresql/data`。

```bash
docker compose -f serviceos-deploy/compose.yaml up -d
```

本地 Portal 使用 `serviceos-local-cli` 客户端走 Authorization Code + PKCE（S256），回调地址为 `http://localhost:5173/*`。开发用户为 `developer`，首次联调密码为 `local-dev-change-me`。

后端首次启动并完成 Flyway 后，为该本地用户建立 ServiceOS RoleGrant：

```bash
docker compose -f serviceos-deploy/compose.yaml exec -T postgres \
  psql -U serviceos_app -d serviceos \
  < serviceos-deploy/keycloak/grant-local-project-admin.sql
```

realm 会把用户属性 `tenant_id` 与 realm roles 映射到 access token 的 `tenant_id`、`capabilities` claim。token capability 仅是声明，ServiceOS 数据库仍必须存在有效 RoleGrant 才会允许命令。生产必须使用组织正式 OIDC、MFA、短期 token、密钥轮换与审批后的 RoleGrant，不使用本地账号。
