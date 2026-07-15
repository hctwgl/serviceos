# 验证与本地环境规则

在选择测试范围、修改 CI、运行 PostgreSQL Testcontainers、发布候选或排查本地构建时读取。

## 验证阶梯

### L0 静态检查

- `git diff --check`；
- Markdown 链接、Context Pack 引用和索引一致性；
- 必要的 Bash、Schema、格式或静态分析。

### L1 快速反馈

```bash
./mvnw --no-transfer-progress -pl serviceos-backend -am -DskipTests compile
./mvnw --no-transfer-progress -pl serviceos-backend -Dtest=RelevantTest test
```

修复失败后先重跑直接失败测试，不重复执行无关全量测试。纯文档修改不要求 Maven 全量构建。

### L2 风险专项

| 改动 | 最低专项证据 |
|---|---|
| 领域规则/Application Service | 相关单元测试 + 用例集成测试 |
| MyBatis/SQL/Flyway | 对应 PostgreSQL IT + 迁移验证 |
| Controller/OpenAPI | MVC/安全测试 + OpenAPI 校验 |
| Event Schema/消费者 | Schema/兼容检查 + Inbox 幂等测试 |
| 授权/Scope | 允许与拒绝路径 + 拒绝审计 |
| Inbox/Outbox/Worker | 事务、重复、claim/lease/retry/recovery |
| 模块声明、公开 API、跨模块调用/事件 | Spring Modulith `ArchitectureTest` |
| shared/bootstrap/父 POM | `ArchitectureTest` + 受影响回归 |

### L3 里程碑门禁

R3 或准备标记 `Implemented` 前至少运行一次完整 `verify`，并执行适用的契约兼容、客户端生成、迁移、staging、回滚或 smoke 演练。

### L4 干净构建/发布

只在发布候选、主分支/PR CI、构建机制变化、从零复现、怀疑缓存污染或用户明确要求时运行 `clean verify`。日常局部修改不得机械重复执行。

## Spring Modulith

以下变化必须显式运行：

```bash
./mvnw --no-transfer-progress -pl serviceos-backend -Dtest=ArchitectureTest test
```

- 新增/删除模块；
- 修改 `package-info.java` 模块声明或 `allowedDependencies`；
- 修改公开 API、跨模块调用或领域事件关系；
- 修改 `shared`、`bootstrap` 或父 POM。

不得通过扩大依赖、开放内部包或移动代码到 `shared` 绕过失败。

## Apple Silicon 与 OrbStack/Docker

本地 PostgreSQL Testcontainers 验证必须优先使用：

```bash
bash scripts/verify-local.sh [Maven 参数]
```

约束：

- 不设置 `DOCKER_DEFAULT_PLATFORM=linux/amd64`；
- 不在 Testcontainers 中强制 amd64；
- 本地已有正确架构的 `postgres:18-alpine` 时复用，不重复 pull；
- 仅首次缺少镜像或设置 `SERVICEOS_TEST_REFRESH_IMAGE=true` 时刷新；
- 脚本不得用于跳过 PostgreSQL、Flyway、事务、并发、授权或 Modulith 门禁；
- CI 可按工作流直接运行 Maven。

## 测试禁令

- 不删除、跳过或隔离失败测试来获得绿色；
- 不放宽核心断言、捕获异常后忽略失败或伪造成功；
- 不用 Mock/H2 替代必须证明的 PostgreSQL、安全或外部协议行为；
- 不把“编译通过”当作事务、迁移、授权、契约或并发证据；
- 未运行的门禁必须在最终报告中明确说明原因和交由谁执行。
