---
title: M13 可观测性、健康探针与日志脱敏验收矩阵
version: 0.1.0
status: Implemented
---

# M13 可观测性、健康探针与日志脱敏验收矩阵

| ID | Priority | 场景 | 预期证据 | 自动化层次 |
|---|---|---|---|---|
| M13-COR-001 | P0 | 合法 correlationId | 响应头、MDC、OTel 上下文使用同一值 | Unit + Web |
| M13-COR-002 | P0 | 非法/疑似凭证 correlationId | 不回显原文，生成 UUID | Unit + Web negative |
| M13-TRC-001 | P0 | API 事务写 Outbox | V011 持久化有效 W3C traceparent/tracestate | PostgreSQL IT |
| M13-TRC-002 | P0 | Worker 异步发布 | consumer Span 与上游 Span 同 trace 且父子关系正确 | In-memory OTel test |
| M13-TRC-003 | P0 | Trace 安全 | payload/token/手机号不进入 Span 属性 | In-memory OTel test |
| M13-HLT-001 | P0 | 进程存活 | `/livez` 为 UP，不依赖数据库之外部系统 | PostgreSQL IT + HTTP |
| M13-HLT-002 | P0 | 应用就绪 | `/readyz` 包含 PostgreSQL 就绪 | PostgreSQL IT + HTTP |
| M13-MET-001 | P0 | Outbox 积压指标 | backlog 与 oldest-age 可抓取 | PostgreSQL IT |
| M13-MET-002 | P0 | 指标访问控制 | 匿名 `/actuator/prometheus` 默认 401 | PostgreSQL IT + HTTP |
| M13-MET-003 | P0 | 指标基数 | 仅 module/result 有界标签，无业务 ID | Code review + test |
| M13-LOG-001 | P0 | ECS JSON 日志 | service/environment/node/correlation 可结构化检索 | Runtime output |
| M13-LOG-002 | P0 | 敏感值脱敏 | 凭证、手机号、VIN、地址、金额被替换 | Unit |
| M13-LOG-003 | P0 | 输出泄露门禁 | 安全 fixture 通过，泄露 fixture 失败 | Shell + CI |
| M13-STK-001 | P0 | 本地栈配置 | Compose、Collector、Tempo、Prometheus、Grafana 配置有效 | Official binaries + jq |
| M13-STK-002 | P0 | 本地栈运行 | Tempo/Prometheus/Grafana healthy，看板与数据源预置 | Docker smoke |
| M13-CI-001 | P0 | 完整构建 | Maven 日志经敏感扫描后才允许通过 | Maven + Shell + CI |

## 验收命令

```bash
runtime_log="${TMPDIR:-/tmp}/serviceos-verify-runtime.log"
./mvnw --batch-mode --no-transfer-progress clean verify 2>&1 \
  | tee "${runtime_log}"
serviceos-deploy/observability/verify-sensitive-output.sh "${runtime_log}"

docker compose -f serviceos-deploy/compose.yaml config --quiet
jq empty serviceos-deploy/observability/grafana/dashboards/serviceos-foundation.json
serviceos-deploy/observability/test-sensitive-output-gate.sh
```

## Trace 串联确定性证据

```bash
./mvnw --batch-mode --no-transfer-progress -pl serviceos-backend \
  -Dtest=OpenTelemetryOutboxTelemetryTest test

./mvnw --batch-mode --no-transfer-progress -pl serviceos-backend \
  -Dit.test=ProjectCommandPostgresIT#outboxPersistsTheCurrentW3cTraceContextForWorkerContinuation verify
```

第一条证明跨异步边界的 traceId/parentSpanId；第二条证明同一上下文在真实 PostgreSQL/Flyway V011 中持久化并能被 Worker 读取。

## 本地栈 Smoke

```bash
docker compose -f serviceos-deploy/compose.yaml up -d tempo otel-collector prometheus grafana
curl --fail http://127.0.0.1:3200/ready
curl --fail http://127.0.0.1:9090/-/ready
curl --fail http://127.0.0.1:3000/api/health
```

Grafana 位于 `http://127.0.0.1:3000`，本地账号见 `serviceos-deploy/README.md`。Prometheus 在后端未启动时显示 target down 是预期状态，不代表 Prometheus 自身不健康。

## 2026-07-13 本地验收证据

| 证据 | 结果 |
|---|---|
| 根仓库 `./mvnw clean verify` | SUCCESS；71 个自动化测试通过，0 失败、0 跳过 |
| Backend 单元/安全/架构测试 | 39/39 通过，0 跳过 |
| PostgreSQL + Flyway 集成测试 | 25/25 通过，0 跳过；V001～V011 真实迁移 |
| `ObservabilityPostgresIT` | 2/2 通过，真实 PostgreSQL 18 + Flyway V001～V011 |
| 异步 Trace 测试 | 同 traceId、正确 parentSpanId，敏感属性不存在 |
| Compose 与组件配置 | Compose、Prometheus、Collector、Tempo、Dashboard 语法通过 |
| 本地栈 Smoke | Tempo、Prometheus、Grafana 健康；Collector ready 日志；数据源/看板已预置 |
| 真实 OTLP 链路 | `/livez` Span 经 Backend → Collector → Tempo 可检索；Prometheus target 为 up |
| 敏感输出门禁 | 完整 Maven 输出通过；泄露样本与缺失文件均被拒绝 |

本地证据不能替代远端 workflow、生产告警和容量验证。
