# ADR-013：采用可自动验证的模块化单体参考工程

- 状态：Proposed
- 日期：2026-07-13

## 背景

ADR-001 已决定 MVP 使用模块化单体，但仅有概念边界不足以阻止跨模块 repository、internal 类和数据库表访问。研发还需要一套可构建、可测试、可部署的参考技术 profile。

## 决策

首期后端采用 Java 21 LTS、Spring Boot 受支持稳定线、Spring Modulith/ArchUnit、Maven Wrapper、PostgreSQL 和 Flyway。应用作为一个版本化部署产物，API 与 worker 可用不同 profile 独立扩容。

每个领域模块拥有公开 `api`、内部 application/domain/infrastructure/web、模块迁移目录和允许依赖清单。CI 必须验证无循环、只访问公开接口、无未声明依赖，并执行模块独立测试。

工单/计价权威版本与 SideEffectFence 的运行时判定独立为 `authority` 模块；`rollout` 只负责 cohort、Gate、切换和回退治理，并通过 authority API 变更权威。authority 不反向依赖 workorder，避免运行时写门禁与治理模块形成循环。

前端维持 Admin、Network 和 Technician 三个独立 Portal；只共享契约类型、设计 token 和无角色假设的基础组件。

## 约束

- 具体补丁版本由 BOM/lock file 固定并自动升级验证；
- shared-kernel 不得成为业务杂物箱；
- 一个 PostgreSQL 数据库不等于允许跨模块表写入；
- 其他模块只能使用公开 Java API/事件和受控查询；
- 生产应用账号无 DDL 权限；
- 拆分服务需满足 ADR-001 的证据触发条件。

## 后果

团队获得统一脚手架和自动边界门禁，首期避免微服务运维复杂度；代价是必须持续维护模块依赖、契约和架构测试，不能用“都在一个进程”绕过设计。

## 复审触发

- Java/Spring 版本停止支持；
- 模块验证工具无法覆盖实际语言/框架；
- 某模块出现持续独立扩缩容、发布、合规或故障隔离证据；
- Portal 用户旅程和发布责任发生实质合并。
