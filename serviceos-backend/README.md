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

## BYD 工单接入事务切片

BYD CPIM 入站端点当前执行：

```text
验签/载荷摘要/试点 DTO 校验
→ configuration 按 tenant/project/brand/service/region/time 解析唯一 Published Bundle
→ Nonce 防重放
→ workorder 按 tenant/client/externalOrderCode 创建或业务重放
→ 同一事务保存响应摘要
```

`wo_work_order` 通过复合外键锁定 tenant、project 与 bundle 的一致引用。配置零/多命中、签名或载荷失败不会占用 Nonce；同业务键冲突载荷不会覆盖原工单。

## 自动 Task 执行

`task` 模块拥有自动任务的唯一重试时钟和执行尝试：

```text
schedule（业务键 + payloadDigest 幂等）
→ 短事务 SKIP LOCKED claim + lease + attempt
→ 事务外 AutomatedTaskHandler
→ 短事务 SUCCEEDED / RETRY_WAIT / MANUAL_INTERVENTION + Outbox
```

只有执行器显式抛出的 `RETRYABLE` 错误才会重试；`UNKNOWN`、`FINAL`、缺处理器和未分类异常全部转人工，避免重复外部副作用。最后一次执行崩溃后，过期租约会被恢复为人工接管，不会永久卡在 `CLAIMED`。

`operations` 模块可靠消费人工接管事件，在同一事务中写入 Inbox、OperationalException，并创建一个 `READY` 的 HUMAN handling Task。正式 Broker 适配器尚未包含在参考实现中。

## 安全文件生命周期

`files` 模块实现：

```text
BeginUpload（幂等会话 + 服务端 object key）
→ 短期大小/MIME 受限 PUT
→ Finalize（真实 size/SHA-256/魔数 MIME）
→ QUARANTINED/PENDING_SCAN + file.content-scan Task
→ CLEAN=AVAILABLE / MALICIOUS=MALWARE 隔离
→ 实时 RoleGrant + purpose 审计 + 短期 GET
```

默认 `local-private` 是可运行开发沙箱，目录不公开并使用 HMAC 短期能力 URL；默认 `local-eicar` 只证明恶意路径协议。生产必须替换为受管对象存储和专业内容安全服务。

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
| `SERVICEOS_BYD_CPIM_TENANT_ID` | `tenant-byd-pilot` | BYD 连接所属租户，生产必须来自连接配置 |
| `SERVICEOS_BYD_CPIM_PROJECT_CODE` | `BYD-OCEAN-SD-PILOT` | BYD 连接绑定项目，不接受请求体覆盖 |
| `SERVICEOS_FILE_STORAGE` | `local-private` | 文件存储适配器；生产禁止使用本地沙箱 |
| `SERVICEOS_FILE_SCANNER` | `local-eicar` | 内容扫描适配器；生产必须使用专业扫描服务 |
| `SERVICEOS_FILE_MAXIMUM_SIZE` | `52428800` | 单文件最大字节数 |
| `SERVICEOS_FILE_LOCAL_ROOT` | 系统临时目录 | 本地私有存储根目录 |
| `SERVICEOS_FILE_LOCAL_SIGNING_KEY` | 本地开发密钥 | 本地短期 URL HMAC；生产必须外置轮换 |
| `SERVICEOS_FILE_TRANSFER_BASE_URL` | `http://localhost:8080/api/v1/file-transfers` | 本地数据面公开基址 |

容器发布额外使用 `SERVICEOS_EXPECTED_MIGRATION_VERSION` 作为发布清单门禁。迁移容器使用 `serviceos_migrator`，业务容器设置 `SPRING_FLYWAY_ENABLED=false` 并使用无 DDL 的 `serviceos_runtime`；两个模式必须来自同一 image digest。参考实现与完整演练见 `serviceos-deploy/staging/`。

## 测试层次

- `ArchitectureTest`：模块循环、未声明依赖和 internal 访问；
- `ProjectTest`：纯领域不变量；
- `ProjectCommandPostgresIT`：真实 PostgreSQL 的空库迁移、重复迁移、幂等和事务原子性。
- `SecurityContextCurrentPrincipalProviderTest`：JWT claim 的唯一映射；
- `DefaultAuthorizationServiceTest`：capability/tenant 拒绝及审计；
- `OutboxWorkerTest`：租约消息发布与失败回退。
- `TaskExecutionWorkerTest`：成功、受控重试、UNKNOWN、未分类异常和租约耗尽恢复；
- `TaskExecutionPostgresIT`：真实 PostgreSQL 的任务幂等、排他 claim、过期租约、attempt/Outbox 和异常人工闭环。
- `DefaultFileCommandServiceTest`：身份来源、Finalize 校验、隔离文件与扫描任务原子编排；
- `LocalPrivateObjectStorageGatewayTest`：短期签名、路径、大小/MIME、一次性 PUT 和下载；
- `FileLifecyclePostgresIT`：真实 PostgreSQL 的 Begin/Finalize/Scan/Download、恶意隔离和 V010 迁移。
- `ConfigurationPublicationPostgresIT`：Published 配置幂等、不可变、作用域优先和重叠拒绝；
- `WorkOrderCommandPostgresIT`：租户级业务幂等、冲突载荷和 tenant/project/bundle 复合外键；
- `BydCpimInboundOrderHttpPostgresIT`：BYD HTTP 到配置锁定与工单落库的端到端事务。
