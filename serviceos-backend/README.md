# ServiceOS Backend

首期后端是一个部署产物内的模块化单体。业务模块按 `com.serviceos.<module>` 顶层包识别，通过 `package-info.java` 声明允许依赖；其他模块只能调用目标模块的 `api` 命名接口。

## 首条纵向切片

`POST /api/v1/projects` 的事务顺序：

```text
OIDC/JWT 身份解析
→ ServiceOS RoleGrant + capability + tenant scope 授权
→ 协议校验
→ 幂等键抢占/重放判断
→ 创建 Project 聚合
→ 追加 AuditRecord
→ 追加 OutboxEvent
→ 冻结幂等成功结果
→ 提交 PostgreSQL 本地事务
```

事务内没有消息发布或外部 HTTP 调用。Outbox worker 在短事务中认领、事务外发布，并在独立短事务保存 attempt；只有注册真实 `OutboxPublisher` 时才会启用，禁止以日志/no-op 伪造成功。

JWT 中的 `capabilities` 只是上游声明，不是授权事实。命令必须命中 ServiceOS 当前有效且未撤销的 RoleGrant；撤权后即使旧 token 仍声明能力也会被拒绝。

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SERVICEOS_DB_URL` | `jdbc:postgresql://localhost:5432/serviceos` | PostgreSQL JDBC URL |
| `SERVICEOS_DB_USERNAME` | `serviceos_app` | 应用账号 |
| `SERVICEOS_DB_PASSWORD` | `serviceos_app` | 仅本地默认值；生产必须使用 secret manager |
| `SERVICEOS_DB_POOL_SIZE` | `10` | 连接池上限 |
| `SERVICEOS_OIDC_JWK_SET_URI` | 本地 Keycloak JWK 地址 | 受信 OIDC 公钥集合；生产必须外置 |
| `SERVICEOS_OIDC_ISSUER_URI` | 本地 ServiceOS realm | JWT `iss` 允许值 |
| `SERVICEOS_OIDC_AUDIENCE` | `serviceos-api` | JWT `aud` 必须包含的 API audience |

## 测试层次

- `ArchitectureTest`：模块循环、未声明依赖和 internal 访问；
- `ProjectTest`：纯领域不变量；
- `ProjectCommandPostgresIT`：真实 PostgreSQL 的空库迁移、重复迁移、幂等和事务原子性。
- `SecurityContextCurrentPrincipalProviderTest`：JWT claim 的唯一映射；
- `DefaultAuthorizationServiceTest`：capability/tenant 拒绝及审计；
- `OutboxWorkerTest`：租约消息发布与失败回退。
