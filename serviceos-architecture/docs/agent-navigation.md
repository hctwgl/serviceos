---
title: ServiceOS Agent 任务导航
lastUpdated: 2026-07-23
---

# ServiceOS Agent 任务导航

本文件回答“完成当前任务最少需要读什么”。不要按历史编号探索，也不要把 Git 历史或已合并 PR 说明当作当前事实。

## 固定起点

1. `git status --short --branch`，保护用户已有修改；
2. 阅读 `docs/implementation-status.md` 的相关能力和未完成边界；
3. 从下表选择一行，只读直接事实源和相邻代码；
4. 通过 `rg` 定位公共 API、契约、迁移和直接测试；
5. 明确本次范围、非目标、风险等级和验证命令后实施。

通常 3～6 个文档足够开工。需要批量通读目录时，先重新确认任务边界。

## 任务路由

| 任务 | 最小必读 | 直接工程入口 |
|---|---|---|
| 产品、页面、交互 | `product-design/README.md`、对应页面边界/旅程/DEC | `serviceos-frontend/apps/<portal>` 或 `serviceos-technician-ios` |
| 前端共享能力 | 产品设计基线、共享边界决策 | `serviceos-frontend/packages`、当前消费者 |
| 身份、组织、授权 | `architecture/07-identity-authorization-audit.md`、相关 Accepted ADR | `identity`/`organization`/`authorization` 模块及安全测试 |
| 项目、工单、任务 | `architecture/03-domain-model.md`、`06-work-order-task-execution-kernel.md` | 对应模块、Core OpenAPI、直接测试 |
| 配置、履约方案、工作流 | `architecture/05-*`、`06-*`、`AD-014-*`、相关产品 DEC | `configuration`/`workflow`、Schema、迁移和测试 |
| 网点、师傅、派单 | `architecture/11-service-network-dispatch.md`、DEC-005/006 | `network`/`dispatch`/`task`、并发与授权测试 |
| 预约、现场、表单、资料 | `architecture/08-*`、`09-*`、`10-*` | 对应模块、Core OpenAPI、PostgreSQL IT |
| SLA、异常、通知 | `architecture/12-*`、`14-*` | `sla`/`operations`、事件 Schema 和恢复测试 |
| OEM 集成 | `architecture/13-integration-reliability.md`、`integration/`、相关 ADR | `integration`、BYD/Core OpenAPI、幂等/验签/重放测试 |
| Inbox/Outbox/Worker | `architecture/20-transaction-messaging-concurrency-blueprint.md`、ADR-014 | `reliability`、真实 PostgreSQL 并发测试 |
| 数据库、MyBatis、Flyway | `architecture/36-persistence-engineering-guideline.md` | Repository 端口、Mapper/XML、迁移和 PostgreSQL IT |
| API 或事件契约 | 相关领域架构和 ADR | `serviceos-contracts` 机器契约及契约测试 |
| 部署、可观测性、安全 | `architecture/21-*`、`18-*`、`docs/local-test-performance.md` | `serviceos-deploy`、构建脚本和适用门禁 |
| 当前状态/证据核对 | `docs/implementation-status.md`、`docs/implementation-traceability-matrix.md` | 代码、契约、Flyway、自动化测试 |

## 判断规则

- 用户最新明确决定 > Accepted ADR/产品 DEC > Accepted 长期架构 > 机器契约/Flyway > 自动化测试 > 当前代码 > README/注释；
- 状态文档只做导航，不覆盖更高优先级事实；
- Draft/Proposed 只提供上下文，不构成实现承诺；
- Git 历史只用于追责和恢复，不用于决定当前行为；
- 实现存在不等于产品已验收，自动化通过也不等于真实环境完成。

## 禁止重新引入

- 逐 PR、逐切片、逐页面的永久 handoff 或总结文档；
- 可从 Git、契约、迁移或测试直接得到的重复清单；
- 递增编号驱动的事实源、索引生成器和完成状态；
- `old`、`legacy`、`archive`、`temporary` 文档目录；
- 已删除应用路径、演示入口或历史截图作为当前产品证据。
