# ServiceOS

ServiceOS 是面向新能源充电设施现场服务的可配置履约平台。当前仓库同时保存架构事实源、参考后端工程和机器可读契约。

## 当前可运行基线

- Java 21 + Spring Boot 4.1 + Spring Modulith 2.1 模块化单体；
- Maven Wrapper 一条命令构建；
- `shared`、`bootstrap`、`identity`、`authorization`、`project`、`audit`、`reliability`、`task`、`operations`、`files` 十个参考模块；
- PostgreSQL + Flyway 模块前缀物理表；
- `CreateProject` 首条命令链路；
- 聚合、审计、幂等结果和 Outbox 同一事务；
- OIDC/JWT 主体、capability/tenant scope 后端授权和拒绝审计；
- Inbox 去重与 Outbox claim/lease/publish worker；
- 自动 Task 调度、执行尝试、租约恢复、受控重试和最终失败人工接管；
- 安全文件 Begin/受限直传/Finalize/隔离扫描/授权下载闭环；
- OpenAPI 3.1 破坏性变更门禁、不可变事件 Schema 治理与可重复 TypeScript 客户端生成；
- W3C API/Outbox/Worker Trace 串联、健康探针、Prometheus 看板与 ECS JSON 日志脱敏；
- Spring Modulith 边界验证与 PostgreSQL Testcontainers 集成测试。

这只是 M8～M13 的工程参考切片，不代表 M6 全部 E1 门禁、正式 IdP/Broker/对象存储/反病毒服务或业务履约链路已经实现。

## 快速验证

```bash
./mvnw clean verify
```

契约变更还必须相对目标 Git 基线执行兼容门禁，并验证客户端可重复生成：

```bash
OASDIFF_BIN="$(serviceos-contracts/scripts/install-oasdiff.sh serviceos-contracts/target/contract-tools)" \
  serviceos-contracts/scripts/check-contract-compatibility.sh origin/master

serviceos-contracts/scripts/verify-client-generation-reproducibility.sh
```

本机存在 Docker/兼容容器运行时时，构建会运行 PostgreSQL 18 集成测试；没有容器运行时时，Testcontainers 会明确显示该组测试被跳过。CI 环境不得把这组 P0 数据库测试标为不适用。

本地启动数据库：

```bash
docker compose -f serviceos-deploy/compose.yaml up -d postgres
./mvnw -pl serviceos-backend spring-boot:run
```

## 目录

```text
serviceos-architecture/  架构、产品、API、数据、测试和路线图事实源
serviceos-backend/       Java 模块化单体参考实现
serviceos-contracts/     OpenAPI 与事件 JSON Schema
serviceos-deploy/        本地/环境部署入口
```

详细工程说明见 [M8 首条事务切片](serviceos-architecture/architecture/22-engineering-reference-implementation.md)、[M9 身份授权与可靠消息](serviceos-architecture/architecture/23-identity-authorization-reliable-worker-implementation.md)、[M10 Task/Scheduler 执行内核](serviceos-architecture/architecture/24-task-scheduler-manual-intervention-implementation.md)、[M11 安全文件生命周期](serviceos-architecture/architecture/25-secure-file-lifecycle-implementation.md)、[M12 契约兼容 CI 与客户端生成](serviceos-architecture/architecture/26-contract-ci-client-generation-implementation.md)和 [M13 可观测性、探针与日志脱敏](serviceos-architecture/architecture/27-observability-health-redaction-implementation.md)。
