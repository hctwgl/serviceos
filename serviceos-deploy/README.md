# ServiceOS Local Infrastructure

本地环境包含 PostgreSQL 18 与 Keycloak 26.7.0。Keycloak realm、用户和密码仅用于开发机，不得复制到测试或生产环境。

PostgreSQL 数据卷挂载到 `/var/lib/postgresql`，与 18 及更高版本官方镜像的版本化 `PGDATA` 约定一致；不要改回 17 及更早版本使用的 `/var/lib/postgresql/data`。

```bash
docker compose -f serviceos-deploy/compose.yaml up -d
```

本地 Portal 使用 `serviceos-local-cli` 客户端走 Authorization Code + PKCE（S256）。
回调与 CORS 覆盖管理端 / 网点端 / 师傅端的 `localhost` 与 `127.0.0.1`（5173–5175）。
开发用户为 `developer`，首次联调密码为 `local-dev-change-me`。

后端首次启动并完成 Flyway 后，为该本地用户建立产品开发读取与 Project/File 命令所需的
ServiceOS RoleGrant：

```bash
docker compose -f serviceos-deploy/compose.yaml exec -T postgres \
  psql -U serviceos_app -d serviceos \
  < serviceos-deploy/keycloak/grant-local-project-admin.sql
```

realm 会把用户属性 `tenant_id` 与 realm roles 映射到 access token 的 `tenant_id`、`capabilities` claim。token capability 仅是声明，ServiceOS 数据库仍必须存在有效 RoleGrant 才会允许命令。生产必须使用组织正式 OIDC、MFA、短期 token、密钥轮换与审批后的 RoleGrant，不使用本地账号。

Admin 产品开发场景数据统一从新前端 Workspace 重置：

```bash
cd serviceos-frontend
corepack pnpm product-data:reset
```

该命令只允许用于可销毁的本地产品开发环境。正式页面不提供初始化按钮，场景数据必须通过
正式业务 API 或应用服务创建，不得绕过领域规则直接伪造核心业务状态。

## 本地可观测性栈

只启动可观测性组件：

```bash
docker compose -f serviceos-deploy/compose.yaml up -d \
  tempo otel-collector prometheus grafana
```

| 服务 | 本地地址 | 本地凭据/用途 |
|---|---|---|
| Grafana | `http://127.0.0.1:3000` | `admin` / `local-observability-change-me` |
| Prometheus | `http://127.0.0.1:9090` | 指标查询 |
| Tempo | `http://127.0.0.1:3200` | Trace API |
| OTel OTLP HTTP | `http://127.0.0.1:4318` | 后端 Trace 导出 |

这些密码和匿名指标开关仅允许本地开发。生产必须使用正式身份认证、Secret 管理、TLS 与受控采集网络。

### 启动可导出 Trace/指标的后端

先启动 PostgreSQL/Keycloak，再用以下本地环境变量启动应用：

```bash
docker compose -f serviceos-deploy/compose.yaml up -d postgres keycloak

SERVICEOS_OTLP_TRACING_ENABLED=true \
SERVICEOS_OTLP_TRACING_ENDPOINT=http://127.0.0.1:4318/v1/traces \
SERVICEOS_TRACE_SAMPLING_PROBABILITY=1.0 \
SERVICEOS_ALLOW_ANONYMOUS_METRICS=true \
./mvnw -pl serviceos-backend spring-boot:run
```

`SERVICEOS_ALLOW_ANONYMOUS_METRICS=true` 是因为本地 Prometheus 从容器访问宿主机。生产默认保持 `false`；如确需匿名抓取，必须先由私网和入口层限制来源。

### 健康与配置 Smoke

```bash
curl --fail http://127.0.0.1:8080/livez
curl --fail http://127.0.0.1:8080/readyz
curl --fail http://127.0.0.1:3200/ready
curl --fail http://127.0.0.1:9090/-/ready
curl --fail http://127.0.0.1:3000/api/health

docker compose -f serviceos-deploy/compose.yaml config --quiet
jq empty serviceos-deploy/observability/grafana/dashboards/serviceos-foundation.json
```

Grafana 自动预置 `ServiceOS Prometheus`、`ServiceOS Tempo` 和 `ServiceOS Foundation Observability` 看板。后端未启动时 Prometheus target 为 down 是预期状态。

后端开启 100% 本地采样后，可验证真实 OTLP 链路：

```bash
curl -H 'X-Correlation-Id: local-otel-smoke-1' http://127.0.0.1:8080/livez

curl --get http://127.0.0.1:3200/api/search \
  --data-urlencode 'q={ name = "http get /livez" }' \
  --data-urlencode 'limit=10' | jq '.traces[] | {traceID, rootServiceName, rootTraceName}'
```

### Trace 串联与敏感输出门禁

确定性验证 API/Outbox/Worker 上下文关系：

```bash
./mvnw -pl serviceos-backend \
  -Dtest=OpenTelemetryOutboxTelemetryTest test

./mvnw -pl serviceos-backend \
  -Dit.test=ProjectCommandPostgresIT#outboxPersistsTheCurrentW3cTraceContextForWorkerContinuation verify
```

扫描 Maven 日志或导出的 Trace/日志文件：

```bash
serviceos-deploy/observability/test-sensitive-output-gate.sh
serviceos-deploy/observability/verify-sensitive-output.sh "${TMPDIR:-/tmp}/serviceos-verify-runtime.log"
```

### 停止本次 Smoke 组件

```bash
docker compose -f serviceos-deploy/compose.yaml stop \
  grafana prometheus otel-collector tempo
```

不要为普通停止执行 `down -v`，否则会删除本地 PostgreSQL、Tempo、Prometheus 和 Grafana 数据卷。

## 隔离的 staging 发布与回滚演练

`compose.staging.yaml` 使用独立 project/volume，不会复用上面的本地开发数据库。一次完整演练包含镜像构建、空库迁移、发布、权限/健康 smoke、旧应用回滚、当前版本恢复和失败关闭负向门禁：

```bash
serviceos-deploy/staging/verify-rehearsal.sh
```

分步执行：

```bash
serviceos-deploy/staging/generate-local-env.sh /tmp/serviceos-staging.env serviceos-backend:local
serviceos-deploy/staging/build-image.sh serviceos-backend:local
serviceos-deploy/staging/deploy.sh /tmp/serviceos-staging.env
serviceos-deploy/staging/smoke.sh /tmp/serviceos-staging.env
serviceos-deploy/staging/cleanup.sh /tmp/serviceos-staging.env
rm -f /tmp/serviceos-staging.env
```

本地生成的 env 文件权限为 `0600` 且不得提交。正式 staging/production 必须把 `SERVICEOS_IMAGE` 设置为 registry digest（`name@sha256:...`），由 Secret Manager 注入凭据，并使用部署平台完成签名验证、滚动发布、审批和证据留存；不得把本地 mutable-image override 带入正式环境。
