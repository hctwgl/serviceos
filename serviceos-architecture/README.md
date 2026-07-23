# ServiceOS Architecture Book

本目录只保存当前有效、需要长期维护的产品与架构事实。开发过程、旧方案、PR 交接和逐切片验收由 Git 历史保存，不在当前树中重复归档。

## 从哪里开始

- Agent 开工：[任务导航](docs/agent-navigation.md)
- 当前能力与缺口：[实施状态](docs/implementation-status.md)
- 能力到代码/契约/测试：[工程追踪矩阵](docs/implementation-traceability-matrix.md)
- 当前产品设计：[产品设计实施基线](product-design/README.md)
- 后端模块地图：`serviceos-backend/AGENTS.md`

## 事实源结构

| 目录 | 内容 | 使用方式 |
|---|---|---|
| `product-design/` | 已确认产品定位、页面边界、核心旅程和产品决策 | 产品与客户端任务首选 |
| `architecture/` | 长期领域、平台、事务、安全和持久化设计 | 后端规则与模块职责 |
| `decisions/` | 已接受或明确在审的 ADR | 长期技术取舍 |
| `integration/` | OEM 接入和适配边界 | 外部集成任务 |
| `configuration/` | 配置元模型与当前模板资产 | 配置发布和运行时 |
| `engineering/` | 中文表达、日志错误、数据库和质量规范 | 工程实现约束 |
| `governance/` | 产品重置、替换范围和文档治理 | 判断保留/删除和破坏性重构边界 |
| `docs/` | 当前状态、导航、追踪、验证说明、术语 | 快速定位 |

机器事实不在本目录：

- HTTP/事件契约：`serviceos-contracts`
- 数据库结构：`serviceos-backend/src/main/resources/db/migration`
- 模块公开边界：`serviceos-backend/src/main/java/com/serviceos/*/api` 与 `package-info.java`
- 实际行为证据：源码和自动化测试

## 核心长期架构

| 主题 | 文档 |
|---|---|
| 产品、业务域、能力和领域模型 | `architecture/00-*` ～ `04-*` |
| 配置、工作流、任务执行 | `architecture/05-*`、`06-*` |
| 身份授权与审计 | `architecture/07-*` |
| 预约、现场、表单、资料审核 | `architecture/08-*` ～ `10-*` |
| 服务网络、派单、SLA、集成 | `architecture/11-*` ～ `13-*` |
| 通知、异常、履约事实、结算 | `architecture/14-*` ～ `16-*` |
| 迁移、试点、工程、事务、安全 | `architecture/17-*` ～ `21-*` |
| 持久化规范 | `architecture/36-persistence-engineering-guideline.md` |
| 多履约方案绑定 | `architecture/AD-014-fulfillment-plan-matching-and-version-binding.md` |
| 全局脱敏开关 | `architecture/system-wide-redaction-switch.md` |

文件名中的旧章节编号只是稳定书目编号，不表示交付阶段或完成状态。

## 状态与冲突

- `Accepted`：可指导实现；
- `Proposed`：待接受，不构成承诺；
- `Draft`：工作材料，不得作为权威；
- `Implemented`：只表示文档声明的范围已有直接工程证据。

冲突时按以下顺序处理：

1. 当前任务中用户明确批准的最新决定；
2. Accepted 产品决策或 ADR；
3. Accepted 长期架构；
4. OpenAPI、事件 Schema、Flyway；
5. 自动化测试；
6. 当前代码；
7. README、注释和历史说明。

无法由高优先级事实判断的核心业务、安全、数据或破坏性契约决策必须请求确认。

## 文档维护

只在事实变化时更新对应长期文档：

- 业务语义或模块职责变化：长期架构；重大取舍新增/替代 ADR；
- 产品边界变化：`product-design/`；
- HTTP/事件或数据结构变化：机器契约或连续 Flyway，并同步必要解释；
- 当前完成/未完成边界变化：`docs/implementation-status.md`；
- 工程入口变化：导航、追踪矩阵或相关 AGENTS。

不要新增逐任务总结、里程碑实现文档、重复验收矩阵、Agent handoff、冻结 archive 或可由仓库机械推导的清单。需要历史时使用 `git log`、`git show` 和 PR 记录。
