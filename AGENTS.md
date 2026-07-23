# ServiceOS Agent Development Guide

本文件是 ServiceOS 仓库级开发入口。目标是在不牺牲领域边界、事务可靠性、安全和可验证性的前提下，让 Agent 基于当前事实快速迭代。

## 1. 工作目录与开工

必须在真实仓库 `/Users/louis/code/serviceos` 工作。每次开始先执行：

```bash
git status --short --branch
```

保留用户已有修改，不向镜像、导出副本或历史任务目录写代码。

然后：

1. 阅读 `serviceos-architecture/docs/implementation-status.md` 的相关能力和缺口；
2. 按 `serviceos-architecture/docs/agent-navigation.md` 选择最小事实集；
3. 用 `rg` 定位相邻代码、公共 API、机器契约、Flyway 和直接测试；
4. 明确范围、非目标、风险等级和验证命令；
5. 再开始实现。

不要按历史编号探索，也不要新增逐任务总结、PR handoff、里程碑实现文档或重复验收矩阵。Git 历史负责保存开发过程。

## 2. 事实源与优先级

固定入口：

- 当前能力和缺口：`serviceos-architecture/docs/implementation-status.md`
- 任务路由：`serviceos-architecture/docs/agent-navigation.md`
- 能力到工程证据：`serviceos-architecture/docs/implementation-traceability-matrix.md`
- 产品事实：`serviceos-architecture/product-design/`
- 长期架构：`serviceos-architecture/architecture/`
- ADR：`serviceos-architecture/decisions/`
- OpenAPI/事件 Schema：`serviceos-contracts/`
- 数据库：`serviceos-backend/src/main/resources/db/migration/`
- 后端模块地图：`serviceos-backend/AGENTS.md`

冲突优先级：

1. 当前任务中用户明确批准的最新决定；
2. Accepted 产品决策或 ADR；
3. Accepted 长期架构；
4. OpenAPI、事件 Schema、Flyway；
5. 自动化测试证明的行为；
6. 当前代码；
7. README、注释和历史说明。

`Draft`/`Proposed` 不构成研发承诺。代码与文档冲突时不默认代码正确；能从高优先级事实判断时直接修复。

下列事项没有高优先级事实时必须请求确认：

- 核心业务状态、责任、计价、SLA、审核处置规则；
- 破坏性 API、事件或数据迁移；
- 授权、Tenant/Project Scope、敏感数据策略；
- 新模块、职责迁移、长期外部兼容或基础设施选型；
- 不可逆外部副作用或生产数据损失。

## 3. 风险和验证

| 等级 | 典型变更 | 最低验证 |
|---|---|---|
| R0 | 文案、注释、索引、无语义重构 | `git diff --check`、链接/格式 |
| R1 | 局部缺陷、单一规则、内部重构 | 编译 + 精准测试；SQL 使用 PostgreSQL IT |
| R2 | 新用例、跨模块、API/事件/Flyway、安全 | 模块回归 + 契约/迁移/安全专项 + 适用 `ArchitectureTest` |
| R3 | 架构、状态机、安全边界、发布候选 | 完整 `verify` + 适用 staging/兼容/回滚 |

小 diff 只要改变授权、租户范围、状态机、事务边界、事件语义或数据库演进，就至少是 R2。

常用入口：

```bash
bash scripts/agent-verify.sh compile
bash scripts/agent-verify.sh test RelevantTest
bash scripts/agent-verify.sh it RelevantPostgresIT
bash scripts/agent-verify.sh arch
bash scripts/agent-verify.sh contracts origin/master
bash scripts/agent-verify.sh frontend
bash scripts/agent-verify.sh docs
bash scripts/verify-repository-preflight.sh
```

Apple Silicon + OrbStack/Docker 的完整验证必须使用：

```bash
bash scripts/verify-local.sh
```

发布候选或从零可复现才使用：

```bash
bash scripts/verify-local.sh clean verify
```

不得设置 `DOCKER_DEFAULT_PLATFORM=linux/amd64`、强制 Testcontainers 使用 amd64、删除/跳过失败测试、放宽核心断言，或用 Mock 替代必须证明的 PostgreSQL/安全行为。

## 4. 模块与分层

ServiceOS 是模块化单体：

```text
Interface / Adapter
    -> Application
    -> Domain
    -> Port / Repository Interface
    -> Infrastructure Adapter
```

- 模块只通过公开 API、领域事件或明确 SPI 协作；
- 禁止跨模块访问内部包、Repository 或表；
- Controller 只做协议适配；
- Application Service 编排用例和事务；
- Domain 维护规则与不变量，不依赖 Web、MyBatis、JDBC 或外部 SDK；
- Infrastructure 隔离技术实现；
- `shared` 只放稳定、无业务归属的最小公共能力；
- 禁止通用 CRUD 绕过状态机、授权、审计、幂等或版本检查。

Spring Modulith 边界失败是阻断问题，不得通过扩大 `allowedDependencies`、开放内部包或移动业务代码到 `shared` 绕过。

## 5. 事务、并发和可靠消息

- 聚合修改、审计、幂等结果和 Outbox 同事务提交；
- 外部请求、事件和回调定义幂等键及重复语义；
- 消费者使用 Inbox 或等价唯一约束去重；
- Worker 使用可恢复 claim/lease/retry，包含上限、退避和人工接管；
- 并发迁移包含状态、版本或等价条件，并检查影响行数；
- 外部网络调用不得位于持有数据库锁的长事务；
- 不得提交后临时发消息、吞异常、伪造成功或把未知结果当成功。

复杂事务必须用中文说明事务边界、幂等键、锁顺序、失败结果和恢复方式。

## 6. 持久化与数据库

详细规范：`serviceos-architecture/architecture/36-persistence-engineering-guideline.md`。

- 业务持久化默认 MyBatis，复杂查询默认 XML；
- Domain/Application 通过 Repository 端口访问数据；
- 核心业务禁止用通用 CRUD 代替领域命令；
- Spring JDBC 只用于需要精确 SQL/锁/并发语义的内核，并说明理由；
- 结构变化只通过连续 Flyway，默认 expand/contract；
- runtime 账号不得有 DDL 权限；
- 多租户数据显式包含 tenant/project scope；
- PostgreSQL 特性、锁、唯一约束和迁移必须由真实 PostgreSQL Testcontainers 证明。

迁移不得留下无期限默认值、双写或旧查询回退。

## 7. API、事件、安全和文件

- OpenAPI 是 HTTP 契约事实源，JSON Schema 是事件契约事实源；
- 修改 Controller/DTO/事件时同步机器契约和契约测试；
- 已发布契约不得原地破坏；破坏性变更必须获批并版本化；
- 身份来自 OIDC/JWT，授权基于 Capability 与 Tenant/Project Scope；
- 不信任客户端 tenant、project、operator 或权限结果；
- 前端隐藏按钮不能代替后端授权；
- OEM 接入必须验签、校验时间窗、防重放并幂等；
- Secret 不得进入仓库、镜像、日志、Trace 或快照；
- 文件生命周期保持 `Begin -> 受限上传 -> Finalize -> 隔离 -> 扫描 -> 授权下载`；
- 新链路保持 Trace Context、correlation ID、结构化日志、脱敏和审计。

授权绕过、租户越权、审计缺失和敏感信息泄露均为阻断问题。

## 8. 新系统演进

ServiceOS 尚处产品重置阶段。默认直接修正模型、契约、迁移、测试和调用方，不为假设中的旧客户端或旧开发数据增加永久兼容。

禁止：

- 长期保留新旧字段、状态、接口、模型或持久化双轨；
- 缺值、非法值或未知状态时静默补默认值或返回默认成功；
- 吞异常后返回空对象、空集合或伪造降级；
- 无真实对象的 `legacy`、`compat`、`fallback`、`temporary` 分支；
- “先兼容以后再删”但没有期限和退出证据。

真实外部系统、已发布客户端或生产数据确需兼容时，必须有 ADR、明确对象、边界适配层、负责人、截止日期、删除条件、迁移/监控/回退方案和自动化证据。

## 9. 代码与中文表达

- 优先用结构表达意图，不用注释掩盖超长方法；
- 复杂业务、事务、并发、SQL、安全和恢复逻辑写中文注释，解释原因、不变量和失败后果；
- 公共 API/SPI、领域命令/事件、复杂 Repository 和重要配置使用中文 Javadoc 或等价说明；
- TODO/FIXME 写明原因、责任范围和删除条件；
- 日志、异常、校验、审计和操作提示优先准确中文，并包含可检索业务上下文；
- 错误码、Schema、事件类型、指标、Trace 属性和数据库对象保持稳定英文。

## 10. 文档同步

只同步被事实变化影响的权威文件：

| 变化 | 必须同步 |
|---|---|
| 纯实现修复，外部事实不变 | 测试；必要时修正失效注释 |
| API/事件/数据库 | 机器契约或 Flyway + 必要语义说明 |
| 领域规则/状态机/模块职责 | 长期架构；重大时 ADR |
| 产品边界/旅程 | `product-design/` 对应基线或决策 |
| 当前完成范围 | `docs/implementation-status.md` |
| 工程入口变化 | 导航、追踪矩阵或 AGENTS |

不要创建历史 archive、旧版副本、里程碑索引、重复验收 Markdown 或可由代码机械推导的清单。

## 11. 完成门禁

交付前确认：

- 请求范围已实现，非目标和已知风险已说明；
- 模块依赖、领域不变量和状态机未破坏；
- 事务、幂等、并发、审计和 Scope 失败关闭；
- 受影响契约、迁移和长期事实源已同步；
- 最低充分测试真实通过，未隐藏失败或跳过门禁；
- 被替代代码、配置、脚本和文档已清理；
- 工作区未混入无关修改。

最终报告至少说明：完成内容、影响模块/契约/迁移、关键保证、实际验证命令与结果、未运行项及原因、工作区和提交状态。
