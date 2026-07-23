# ServiceOS

ServiceOS 是面向新能源充电设施现场服务的可配置履约平台。仓库包含模块化后端、机器契约、Web Workspace、Technician iOS、部署入口和当前架构事实源。

## 当前边界

- Java 21 + Spring Boot + Spring Modulith 模块化单体；
- PostgreSQL + Flyway + MyBatis；
- OpenAPI 3.1、事件 JSON Schema、TypeScript/Swift 客户端生成；
- Inbox/Outbox、幂等、claim/lease/retry、审计与 W3C Trace；
- OIDC/JWT、Capability、Tenant/Project/Region/Network Scope；
- Admin、Network、Technician 三个独立 Web 应用位于同一 pnpm Workspace；
- Technician 原生 iOS 工程保留独立发布与设备能力边界。

详细的已实现范围和生产化缺口见 [当前实施状态](serviceos-architecture/docs/implementation-status.md)。实现存在不代表产品已经验收。

## 目录

```text
serviceos-backend/         Java 模块化单体
serviceos-contracts/       OpenAPI、事件 Schema 和客户端生成
serviceos-frontend/        Admin / Network / Technician Web Workspace
serviceos-technician-ios/  原生 Technician iOS
serviceos-ios-core/        iOS 共享基础
serviceos-web-core/        仍在迁移中的 Web 共享基础
serviceos-deploy/          本地、产品开发与 staging
serviceos-architecture/    当前产品与架构事实源
scripts/                   当前构建和验证入口
```

## 开发入口

开始任务前：

```bash
git status --short --branch
```

随后按 [Agent 任务导航](serviceos-architecture/docs/agent-navigation.md) 读取最小事实集。后端模块地图见 [serviceos-backend/AGENTS.md](serviceos-backend/AGENTS.md)，产品设计入口见 [product-design/README.md](serviceos-architecture/product-design/README.md)。

## 快速验证

后端编译与精准测试：

```bash
bash scripts/agent-verify.sh compile
bash scripts/agent-verify.sh test RelevantTest
bash scripts/agent-verify.sh it RelevantPostgresIT
bash scripts/agent-verify.sh arch
```

契约、前端和文档：

```bash
bash scripts/agent-verify.sh contracts origin/master
bash scripts/agent-verify.sh frontend
bash scripts/agent-verify.sh docs
```

仓库静态/迁移预检：

```bash
bash scripts/verify-repository-preflight.sh
bash scripts/migration-baseline.sh
```

Apple Silicon + OrbStack/Docker 的完整 Maven 验证：

```bash
bash scripts/verify-local.sh
```

`verify-local.sh` 默认复用本机原生架构 PostgreSQL 镜像；不修改用户永久环境，也不跳过 PostgreSQL、Flyway、事务、安全或模块边界门禁。

Web Workspace：

```bash
cd serviceos-frontend
corepack pnpm install
corepack pnpm check
corepack pnpm product-data:reset
```

本地数据库与后端：

```bash
docker compose -f serviceos-deploy/compose.yaml up -d postgres
./mvnw -pl serviceos-backend spring-boot:run
```

## 文档原则

当前树只保存长期产品/架构事实和直接工程入口。开发过程、旧方案、PR 交接和逐切片记录从 Git 历史或 PR 查询，不在仓库新增 `archive`、`legacy`、里程碑总结或重复验收文档。
