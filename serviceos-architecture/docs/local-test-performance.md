---
title: 本地测试性能与原生 PostgreSQL Testcontainers
version: 0.1.0
status: Implemented
---

# 本地测试性能与原生 PostgreSQL Testcontainers

## 1. 问题说明

ServiceOS 的数据库、并发、事务、唯一约束和 Flyway 行为必须通过真实 PostgreSQL Testcontainers 验证，不能为了缩短时间改用 H2、Mock 或跳过集成测试。

在 Apple Silicon Mac + OrbStack 环境中，如果 shell、IDE 或容器运行时设置了：

```bash
DOCKER_DEFAULT_PLATFORM=linux/amd64
```

即使仓库使用支持多架构的官方 `postgres:18-alpine` 镜像，Docker 仍可能拉取并运行 x86_64/amd64 变体。ARM 主机需要通过模拟器执行该容器，数据库启动、Flyway 迁移和集成测试都会显著变慢。

该问题属于本地容器平台选择错误，不应通过减少数据库测试覆盖来规避。

## 2. 推荐验证入口

本地验证优先使用：

```bash
bash scripts/verify-local.sh
```

无参数时等价于：

```bash
./mvnw --no-transfer-progress verify
```

但在 Maven/Testcontainers 启动前，脚本会：

1. 检测宿主机 CPU 架构；
2. 检查 Docker/OrbStack 是否可用；
3. 仅在当前子进程中移除 `DOCKER_DEFAULT_PLATFORM`；
4. 预拉宿主机原生架构的 `postgres:18-alpine`；
5. 校验本地镜像实际架构；
6. 架构不一致时失败关闭，不允许静默使用模拟模式；
7. 原样执行传入的 Maven 参数，不跳过测试。

输出模式默认为 `auto`：交互终端显示完整实时日志；Agent/CI 等非交互调用只显示最终摘要，
完整 Maven 日志写入 `target/verification-logs/`。失败时精简模式仍返回原始非零退出码、关键错误、
日志尾部和完整日志路径。需要诊断实时输出时执行：

```bash
SERVICEOS_VERIFY_OUTPUT=full bash scripts/verify-local.sh
```

`SERVICEOS_VERIFY_OUTPUT` 只控制 `auto|full|compact` 三种日志呈现，不改变 Maven 参数、测试范围、
Testcontainers、Flyway、契约或模块边界门禁。

## 3. 分级验证示例

### 3.1 指定测试类

```bash
bash scripts/verify-local.sh \
  -pl serviceos-backend \
  -Dtest=RelevantPostgresIT \
  test
```

### 3.2 模块编译

```bash
bash scripts/verify-local.sh \
  -pl serviceos-backend \
  -am \
  -DskipTests \
  compile
```

### 3.3 里程碑全量验证

```bash
bash scripts/verify-local.sh verify
```

### 3.4 从零干净验证

仅在 AGENTS.md 规定的 L5 场景使用：

```bash
bash scripts/verify-local.sh clean verify
```

## 4. 环境诊断

查看是否存在全局平台覆盖：

```bash
echo "${DOCKER_DEFAULT_PLATFORM:-<未设置>}"
```

在 Apple Silicon 上，若输出 `linux/amd64`，应检查并删除 shell 配置、IDE 启动配置或本地自动化脚本中的永久设置。例如检查：

```bash
grep -R "DOCKER_DEFAULT_PLATFORM" \
  ~/.zshrc ~/.zprofile ~/.bashrc ~/.bash_profile 2>/dev/null
```

当前终端可临时取消：

```bash
unset DOCKER_DEFAULT_PLATFORM
```

查看镜像实际架构：

```bash
docker image inspect postgres:18-alpine \
  --format '{{.Os}}/{{.Architecture}}'
```

Apple Silicon 预期结果：

```text
linux/arm64
```

Intel/AMD 主机预期结果：

```text
linux/amd64
```

如果缓存了错误架构镜像，可删除后由验证脚本重新拉取：

```bash
docker image rm postgres:18-alpine
bash scripts/verify-local.sh verify
```

## 5. 可配置项

默认测试镜像：

```text
postgres:18-alpine
```

确需验证其他官方多架构镜像时，可临时设置：

```bash
SERVICEOS_TEST_POSTGRES_IMAGE=postgres:18 \
  bash scripts/verify-local.sh verify
```

未知或特殊宿主机架构可显式指定：

```bash
SERVICEOS_TEST_NATIVE_PLATFORM=linux/arm64 \
  bash scripts/verify-local.sh verify
```

不得使用这些变量强制本机进入与宿主 CPU 不一致的生产级默认平台。

## 6. 质量边界

本优化只修复本地镜像架构选择，不改变以下质量门禁：

- PostgreSQL Testcontainers 仍然真实运行；
- Flyway 仍从真实 PostgreSQL 验证；
- Spring Modulith ArchitectureTest 仍为独立门禁；
- 授权、事务、幂等、并发和契约测试不得跳过；
- 里程碑完成前仍需执行 AGENTS.md 规定的 L4 全量 `verify`；
- PR、主分支 CI 和发布候选仍按 L5 执行干净构建。

## 7. 后续可独立评估的优化

当前部分 PostgreSQL 集成测试类各自声明 Testcontainers 容器。后续可通过独立工程任务评估：

1. 同一 Maven 测试 JVM 内共享 PostgreSQL 容器；
2. 统一 Spring 测试 Profile 与 `DynamicPropertySource`；
3. 减少不必要的 `@DirtiesContext` 和上下文差异；
4. 测试数据库使用 tmpfs；
5. 本地可选 Testcontainers reuse，CI 继续使用全新容器。

共享容器会改变测试隔离、清理、并发执行和 Flyway 生命周期，必须先盘点全部测试夹具并补充隔离证明，不能为加速而仓促统一。
