---
title: 当前工程拓扑与模块边界
version: 1.0.0
status: Accepted
lastUpdated: 2026-07-23
---

# 当前工程拓扑与模块边界

## 1. 目的与事实源

本文描述当前仓库实际存在的构建单元、包边界和验证入口，不保存未来拆分设想。

机器事实优先级：

1. 根 `pom.xml` 的 Maven modules；
2. 后端各模块 `package-info.java` 的 Spring Modulith 声明；
3. `serviceos-frontend/pnpm-workspace.yaml` 与各 `package.json`；
4. Swift `Package.swift`、Xcode workspace/project；
5. Docker Compose 与当前验证脚本；
6. 本文和 README。

本文与机器事实不一致时应修正文档，不能为满足旧拓扑描述创建空模块或兼容目录。

## 2. 仓库拓扑

```text
serviceos/
├── pom.xml                         # Maven Reactor：backend + contracts
├── serviceos-backend/              # Java 21 模块化单体
├── serviceos-contracts/            # OpenAPI、事件 Schema、客户端生成
├── serviceos-frontend/             # pnpm Web Workspace
│   ├── apps/                       # admin / network / technician
│   ├── packages/                   # 4 个 ServiceOS 共享包
│   └── vben/                       # Admin 使用的上游 UI 基础包
├── serviceos-ios-core/             # Swift 6 共享基础
├── serviceos-technician-ios/       # Technician 原生 iOS
├── serviceos-deploy/               # local / product-development / observability / staging
├── serviceos-architecture/         # 当前产品与架构事实源
└── scripts/                        # 全仓验证和发布入口
```

已删除的独立 Admin/Network/Technician Web 仓内根目录以及 `serviceos-web-core` 不再属于当前拓扑。

## 3. 构建单元

| 构建系统 | 入口 | 当前单元 |
|---|---|---|
| Maven | 根 `pom.xml` | `serviceos-backend`、`serviceos-contracts` |
| pnpm | `serviceos-frontend/package.json` | 3 个应用、4 个 `@serviceos/*` 共享包、Admin 使用的 Vben 基础包 |
| SwiftPM | `serviceos-ios-core/Package.swift` | `ServiceOSIOSCore` |
| SwiftPM/Xcode | `serviceos-technician-ios/Package.swift`、`.xcworkspace` | `TechnicianIOSFoundation` 与 Technician App |
| Docker Compose | `serviceos-deploy/compose*.yaml` | 本地依赖与隔离 staging |

根 Maven Reactor 不包含前端或 iOS。跨技术栈验证由 `scripts/agent-verify.sh` 编排。

## 4. 后端模块化单体

后端是单一 Maven artifact，不是每个领域一个 Maven 子模块。一级 Java 包即 Spring Modulith 模块：

```text
com.serviceos.<module>/
├── api/              # 跨模块公开 API
├── spi/              # 可选的显式扩展端口
├── application/      # 用例编排与事务
├── domain/           # 可选的领域模型
├── infrastructure/   # Repository、Mapper、外部适配
├── web/              # 可选的 HTTP 适配
└── package-info.java # 模块声明与允许依赖
```

当前 23 个模块：

| 分组 | 模块 |
|---|---|
| 身份与治理 | `identity`、`organization`、`authorization`、`audit` |
| 履约核心 | `project`、`workorder`、`workflow`、`task` |
| 现场服务 | `network`、`dispatch`、`appointment`、`fieldwork`、`forms`、`evidence`、`files` |
| 运营与集成 | `sla`、`operations`、`integration`、`readmodel` |
| 平台基础 | `configuration`、`reliability`、`shared`、`bootstrap` |

不存在独立的 `authority`、`automation`、`review`、`notification`、`facts`、`pricing`、
`settlement`、`migration` 或 `rollout` 运行时模块。相关概念若已实现，归属以当前
`package-info.java`、公共 API 和数据库表所有权为准；若未实现，不得从历史提案推断存在。

详细职责、表前缀和测试布局见 `serviceos-backend/AGENTS.md`。

## 5. Web Workspace

产品应用：

| 路径 | 包名 | 边界 |
|---|---|---|
| `apps/admin` | `@serviceos/admin` | 平台管理端 |
| `apps/network` | `@serviceos/network` | 网点协作端 |
| `apps/technician` | `@serviceos/technician` | 师傅在线 Web 参考实现 |

共享包：

| 路径 | 包名 | 边界 |
|---|---|---|
| `packages/api-client` | `@serviceos/api-client` | HTTP 协议适配与 Problem 映射 |
| `packages/auth-context` | `@serviceos/auth-context` | OIDC PKCE、Token 会话和上下文 |
| `packages/design-system` | `@serviceos/design-system` | 设计令牌和 UI 组件导出 |
| `packages/product-language` | `@serviceos/product-language` | 产品术语映射 |

依赖方向为应用依赖共享包，共享包不得反向依赖应用，应用之间不得互相导入源码。
`vben/` 是 Admin 的上游 UI 基础，不承载 ServiceOS 业务规则。完整规则见
`serviceos-frontend/AGENTS.md`。

## 6. 契约、iOS 与部署

- `serviceos-contracts` 拥有 Core/BYD OpenAPI、版本化事件 Schema、客户端身份和设计令牌源；
- 生成的 TypeScript/Swift 客户端只存在于 `serviceos-contracts/target`，不得提交或复制为第二事实源；
- `serviceos-ios-core` 只提供无 Portal/角色假设的 Swift 共享基础；
- `serviceos-technician-ios` 拥有原生 App、Keychain/OIDC/设备能力和 Xcode 发布边界；
- `serviceos-deploy/compose.yaml` 服务本地开发；
- `serviceos-deploy/product-development` 只重建可销毁的本地产品场景；
- `serviceos-deploy/observability` 保存本地 OTel/Prometheus/Tempo/Grafana 配置；
- `serviceos-deploy/staging` 保存隔离迁移、Smoke、回滚和恢复演练。

staging 的预期 Flyway 版本和数量必须由 `scripts/migration-baseline.sh` 生成，不保存手工数字样例。

## 7. 验证入口

| 范围 | 命令 |
|---|---|
| 工程拓扑、脚本语法、迁移基线 | `bash scripts/verify-repository-preflight.sh` |
| 后端编译/精准测试 | `bash scripts/agent-verify.sh compile/test/it/arch` |
| 契约兼容 | `bash scripts/agent-verify.sh contracts <base>` |
| 客户端生成与独立消费 | `bash scripts/agent-verify.sh client-foundation` |
| 三个 Web 应用和共享包 | `bash scripts/agent-verify.sh frontend` |
| iOS 共享基础与 App | `bash scripts/agent-verify.sh ios-core/technician-ios*` |
| Maven 全量门禁 | `bash scripts/verify-local.sh` |
| 隔离 staging 演练 | `serviceos-deploy/staging/verify-rehearsal.sh` |

验证脚本发现旧路径时应修复到当前构建单元；不得重建被删除目录、改为空成功脚本或跳过真实消费者。

## 8. 拓扑变更规则

- 新增或删除 Maven module：同步根 `pom.xml`、仓库预检和本文；
- 新增或删除 Modulith 模块：同步 `package-info.java`、`ArchitectureTest`、后端 Agent 地图和本文；
- 新增或删除 pnpm 包：同步依赖消费者、`pnpm-lock.yaml`、前端 Agent 地图和统一前端门禁；
- 新增或删除 Swift/Xcode 单元：同步 Package/Xcode 引用、iOS 门禁和本文；
- 新增长期模块或迁移职责：先确认领域所有权；重大变化记录 ADR；
- 不为未来可能拆分的服务预建空目录、占位包或永远成功的验证脚本。
