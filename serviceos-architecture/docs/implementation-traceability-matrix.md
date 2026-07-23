---
title: ServiceOS 当前工程追踪矩阵
version: 1.0.0
status: Accepted
lastUpdated: 2026-07-23
---

# ServiceOS 当前工程追踪矩阵

本矩阵用于快速定位当前实现证据，不复述历史切片。业务语义看长期架构与 Accepted ADR；对外行为看机器契约；数据库事实看 Flyway；实际行为看源码和直接测试。

## 1. 后端

| 能力 | 实现入口 | 长期设计 | 直接证据 |
|---|---|---|---|
| 身份与组织 | `serviceos-backend/src/main/java/com/serviceos/identity`、`organization` | `architecture/07-identity-authorization-audit.md`、产品决策 | 同名测试目录、OpenAPI、Flyway |
| 授权与审计 | `authorization`、`audit` | `architecture/07-*`、`21-security-*` | MVC 安全测试、拒绝审计测试、`ArchitectureTest` |
| 项目与工单 | `project`、`workorder` | `architecture/01-*`、`03-*`、`06-*` | 同名测试目录、Core OpenAPI、`prj_`/`wo_` 迁移 |
| 配置与工作流 | `configuration`、`workflow` | `architecture/05-*`、`06-*`、`AD-014-*` | 配置/工作流测试、Schema、`cfg_`/`wfl_` 迁移 |
| 任务与派单 | `task`、`dispatch`、`network` | `architecture/06-*`、`11-*`、`20-*` | 并发/恢复 PostgreSQL IT、Core OpenAPI、相关迁移 |
| 预约与现场 | `appointment`、`fieldwork` | `architecture/08-*` | 状态机测试、PostgreSQL IT、Core OpenAPI |
| 表单与资料 | `forms`、`evidence`、`files` | `architecture/09-*`、`10-*` | 版本/完成门禁/文件授权测试、Schema、相关迁移 |
| SLA 与异常 | `sla`、`operations` | `architecture/12-*`、`14-*` | 时钟/对账/异常恢复测试、Core OpenAPI |
| 外部集成 | `integration` | `architecture/13-*`、`integration/`、Accepted ADR | BYD OpenAPI、事件 Schema、验签/幂等/重放测试 |
| 可靠消息 | `reliability` | `architecture/20-*`、ADR-014 | Inbox/Outbox/claim/lease/retry PostgreSQL IT |
| 只读投影 | `readmodel` | 模块公开 API 与相关领域架构 | 投影消费/授权查询/重建测试、`rdm_` 迁移 |

公共模块地图和测试布局见 `serviceos-backend/AGENTS.md`。禁止通过本矩阵推断未列出的跨模块依赖。

## 2. 契约与数据

| 事实 | 权威位置 | 验证入口 |
|---|---|---|
| Core HTTP API | `serviceos-contracts/src/main/resources/openapi/serviceos-core-v1.yaml` | `bash scripts/agent-verify.sh contracts` |
| BYD CPIM API | `serviceos-contracts/src/main/resources/openapi/byd-cpim-v731.yaml` | 契约测试与集成模块测试 |
| 领域事件 | `serviceos-contracts/src/main/resources/events/` | Schema 不可变与兼容测试 |
| 数据库结构 | `serviceos-backend/src/main/resources/db/migration/` | `bash scripts/migration-baseline.sh`、PostgreSQL IT |
| TypeScript/Swift 客户端 | `serviceos-contracts` 生成脚本与消费者 | `client-ts`、`client-swift`、`client-foundation` |

Markdown API/data 文档用于解释语义，不能覆盖机器契约或迁移。

## 3. 客户端与产品

| 范围 | 实现入口 | 产品事实 | 验证 |
|---|---|---|---|
| Admin Web | `serviceos-frontend/apps/admin` | `product-design/` | `bash scripts/agent-verify.sh frontend` + 真实环境人工产品评审 |
| Network Web | `serviceos-frontend/apps/network` | `product-design/` 与 DEC-006 | `bash scripts/agent-verify.sh frontend` + 真实环境人工产品评审 |
| Technician Web | `serviceos-frontend/apps/technician` | `product-design/` | `bash scripts/agent-verify.sh frontend` |
| Technician iOS | `serviceos-technician-ios` | 核心旅程和客户端边界 | `agent-verify.sh technician-ios*`、Simulator/真机证据 |
| 共享 Web/iOS | `serviceos-frontend/packages/{api-client,auth-context,design-system,product-language}`、`serviceos-ios-core` | 跨产品共享边界 | `frontend`、`client-foundation`、`ios-core` |

自动化通过只证明对应门禁；视觉、流程可用性和产品完成状态必须由当前产品验收另行确认。

## 4. 变更同步

- 领域规则或职责变化：更新长期架构；重大取舍新增或替代 ADR；
- HTTP/事件变化：更新机器契约和契约测试；
- 数据结构变化：新增连续 Flyway 和 PostgreSQL IT；
- 产品决策变化：更新 `product-design/` 中的基线或决策；
- 当前完成边界变化：只更新 `implementation-status.md`；
- Git 历史承担过程追溯，不在仓库新增 PR handoff、逐切片总结或重复验收 Markdown。
