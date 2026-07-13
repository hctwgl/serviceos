# ServiceOS Backend

首期后端是一个部署产物内的模块化单体。业务模块按 `com.serviceos.<module>` 顶层包识别，通过 `package-info.java` 声明允许依赖；其他模块只能调用目标模块的 `api` 命名接口。

## 首条纵向切片

`POST /api/v1/projects` 的事务顺序：

```text
协议校验
→ 幂等键抢占/重放判断
→ 创建 Project 聚合
→ 追加 AuditRecord
→ 追加 OutboxEvent
→ 冻结幂等成功结果
→ 提交 PostgreSQL 本地事务
```

事务内没有消息发布或外部 HTTP 调用。Outbox worker、OIDC 与权限引擎属于后续 E1 增量，不应从当前代码存在性推断已经完成。

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SERVICEOS_DB_URL` | `jdbc:postgresql://localhost:5432/serviceos` | PostgreSQL JDBC URL |
| `SERVICEOS_DB_USERNAME` | `serviceos_app` | 应用账号 |
| `SERVICEOS_DB_PASSWORD` | `serviceos_app` | 仅本地默认值；生产必须使用 secret manager |
| `SERVICEOS_DB_POOL_SIZE` | `10` | 连接池上限 |

## 测试层次

- `ArchitectureTest`：模块循环、未声明依赖和 internal 访问；
- `ProjectTest`：纯领域不变量；
- `ProjectCommandPostgresIT`：真实 PostgreSQL 的空库迁移、重复迁移、幂等和事务原子性。
