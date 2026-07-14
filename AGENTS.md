# ServiceOS Agent Development Guide

本文件是 ServiceOS 仓库级开发入口。任何在本仓库中分析、生成、修改、测试或评审代码的 Agent 与开发者，都必须先阅读本文件，并按照 `serviceos-architecture/` 中的事实源实施。

`AGENTS.md` 不替代详细架构文档。它负责规定开发顺序、事实源优先级、全局工程约束、实施状态维护和变更完成标准。

---

## 1. 项目定位

ServiceOS 是面向新能源充电设施现场服务的可配置履约平台。当前采用 Java 21、Spring Boot、Spring Modulith、PostgreSQL、Flyway、OpenAPI、事件 Schema、Inbox/Outbox、OIDC/JWT、Capability 和 Tenant/Project Scope。

当前目标是建立边界清晰、事务可靠、可验证、可演进的履约内核。不得为了形式上的微服务、框架统一或短期 CRUD 速度，破坏领域边界、事务一致性和可测试性。

---

## 2. 架构与实施事实源

`serviceos-architecture/` 是产品、领域、架构、API、数据、测试、部署和路线图的事实源。

关键入口：

- `serviceos-architecture/README.md`：Architecture Book 导航；
- `serviceos-architecture/docs/implementation-status.md`：统一实施状态总览；
- `serviceos-architecture/docs/implementation-traceability-matrix.md`：能力到代码、契约和测试的追踪；
- `serviceos-architecture/architecture/`：总体设计、工程规范和 Mxx 实现文档；
- `serviceos-architecture/testing/`：Mxx 验收矩阵；
- `serviceos-architecture/decisions/`：ADR；
- `serviceos-contracts/`：OpenAPI 和事件 Schema；
- `serviceos-backend/src/main/resources/db/migration/`：数据库迁移事实。

Agent 开始开发前必须定位并阅读相关文档，不得只根据现有代码、类名或一句需求直接实现。

### 实施状态判断

- `Draft`：正在形成，不可作为研发承诺；
- `Proposed`：完整提案，尚未接受或实施；
- `Accepted`：设计已接受，可指导实现，但不代表代码完成；
- `Implemented`：对应里程碑声明范围已有代码、契约、迁移和适用自动化证据。

`Implemented` 不代表整个领域完成。必须同时阅读对应实现文档的“明确未实现”和验收矩阵。

---

## 3. 事实优先级

发生冲突时按以下顺序判断：

1. 当前任务中用户明确批准的最新决策；
2. 已接受 ADR；
3. 状态为 Accepted/Implemented 的架构与验收文档；
4. OpenAPI、事件 Schema、Flyway 等机器契约；
5. 自动化测试证明的行为；
6. 当前实现代码；
7. README、注释和历史说明。

不得因为代码与文档不一致就默认代码正确。无法判断时，必须在变更说明中明确冲突、判断依据和待决事项。

---

## 4. 开发前必须完成

修改代码前必须：

1. 明确产品能力、领域模块和里程碑；
2. 阅读相关产品、架构、ADR、API、数据、测试和实施状态文档；
3. 检查模块公共 API 和依赖方向；
4. 检查 OpenAPI、事件 Schema、Flyway、验收矩阵和追踪矩阵；
5. 判断本次是实现既有设计、修复架构偏离，还是变更产品/架构事实；
6. 列出适用验收项和验证命令；
7. 确认 `implementation-status.md` 中当前已完成和未完成边界；
8. 然后才能编码。

禁止先写代码，再寻找文档为实现背书。

---

## 5. 模块、领域与分层

ServiceOS 是模块化单体，不是无边界单体。

```text
Interface / Adapter
    -> Application
    -> Domain
    -> Port / Repository Interface
    -> Infrastructure Adapter
```

必须遵守：

- 模块只能通过公开 API、领域事件或明确 SPI 协作；
- 禁止跨模块访问内部包、内部 Repository 或内部表；
- Controller 只负责协议适配和输入输出转换；
- Application Service 负责编排用例和事务；
- Domain 负责规则、状态迁移和不变量；
- Infrastructure 负责数据库、消息、文件和第三方技术实现；
- 领域模型不得依赖 Web、MyBatis、JDBC、消息客户端或外部 SDK；
- 禁止通用 CRUD 绕过状态机、授权、审计、幂等和版本检查；
- `shared` 只允许稳定、无业务归属的最小公共能力；
- 新增模块或调整职责必须更新架构文档，重大变化必须更新 ADR。

模块边界测试失败属于阻断问题。

---

## 6. 事务、幂等与可靠消息

- 聚合修改、审计、幂等结果和 Outbox 必须在同一数据库事务中提交；
- 外部请求、消息和回调必须定义幂等键及重复语义；
- Inbox/Outbox 不得被普通异步调用或提交后再发消息替代；
- Worker 必须使用可恢复的 claim/lease/retry；
- 并发状态迁移必须包含状态、版本或等价条件并检查影响行数；
- 外部网络调用不得位于持有数据库行锁的长事务；
- 自动重试必须有上限、退避和人工接管；
- 不得吞掉异常或伪造成功。

---

## 7. 持久化与数据库

详细规范见 `serviceos-architecture/architecture/36-persistence-engineering-guideline.md`。

- 普通业务持久化默认使用 MyBatis；
- 复杂查询默认使用 MyBatis XML；
- Domain/Application 不得直接依赖 Mapper、JdbcTemplate 或 JdbcClient；
- 必须通过 Repository 端口和 Infrastructure Adapter 隔离框架；
- 核心业务默认禁止 MyBatis-Plus BaseMapper/IService/ServiceImpl 通用 CRUD；
- Spring JDBC 仅用于确需直接控制底层 SQL 语义的可靠性或并发内核，采用时必须说明理由；
- 所有结构变化必须通过连续 Flyway 迁移；
- 数据库演进默认 expand/contract；
- runtime 账号不得拥有 DDL 权限；
- 多租户数据必须显式包含 tenant/project scope；
- 锁、claim、lease、并发更新、唯一约束和 PostgreSQL 特性必须由真实 PostgreSQL Testcontainers 测试证明；
- 禁止仅使用 H2 或 Mock 证明 PostgreSQL 行为。

---

## 8. API、事件、身份与安全

- OpenAPI 是 HTTP 契约事实源；
- JSON Schema 是领域和集成事件契约事实源；
- 不得只修改 Controller/DTO 而不同步契约；
- 已发布事件不得原地破坏字段语义；
- 破坏性 API 变更必须获批并通过兼容门禁；
- 事件消费者必须使用 Inbox 去重；
- 契约变更必须验证客户端生成可重复性；
- 身份来源为 OIDC/JWT；
- 后端授权基于 Capability 与 Tenant/Project Scope；
- 不得信任客户端 tenant、project、operator 或权限结果；
- 前端隐藏按钮不能替代后端授权；
- 车企接入必须执行签名、时间窗、防重放和幂等；
- Secret 不得提交到仓库或写入镜像。

授权绕过、租户越权和审计缺失属于阻断问题。

---

## 9. 文件、集成与可观测性

文件生命周期必须保持：

```text
Begin -> 受限上传 -> Finalize -> 隔离 -> 扫描 -> 授权下载
```

禁止未扫描文件直接下载、公共静态暴露私有文件、数据库长期保存大文件正文、永久暴露对象存储 URL，或在领域模块直接耦合云厂商 SDK。

外部系统必须通过 integration 适配层完成协议转换、鉴权、防重放、错误映射和可观测性。

新增链路必须保持 W3C Trace Context、correlation ID、结构化日志、敏感字段脱敏、业务审计、正确的 liveness/readiness。日志、指标和 Trace 不得泄露凭据或敏感信息。

---

## 10. 测试与验收

根据风险选择：

- 领域规则：单元测试；
- 模块边界：Spring Modulith；
- 数据库：PostgreSQL Testcontainers；
- 安全：认证、Capability、Scope 和拒绝审计测试；
- API：接口和兼容性测试；
- 事件：Schema 与幂等消费测试；
- Worker：claim、lease、重试、恢复和并发测试；
- 部署：迁移、readiness、smoke 和回滚演练。

Agent 必须读取对应验收矩阵，将适用条目实现为自动化证据，或明确说明未满足原因。

禁止删除/跳过失败测试、放宽核心断言、将 CI 必跑测试改为跳过、用 Mock 替代必须验证的真实数据库/安全行为，或捕获异常后忽略失败。

---

## 11. 文档与实施状态同步

产品范围、领域规则、模块职责、聚合、状态机、API、事件、数据模型、安全、部署、验收或里程碑状态发生变化时，必须同步更新 `serviceos-architecture/`。

每个新里程碑或已实现范围变化，必须在同一提交或同一 PR 中同步更新：

1. `serviceos-architecture/README.md`；
2. 对应 `architecture/Mxx` 实现文档；
3. 对应 `testing/Mxx` 验收矩阵；
4. `serviceos-architecture/docs/implementation-traceability-matrix.md`；
5. `serviceos-architecture/docs/implementation-status.md`；
6. 根 README 的当前可运行基线；
7. 相关总体设计文档中的已实现和未实现边界；
8. 适用的 OpenAPI、事件 Schema、Flyway 和部署迁移清单。

`implementation-status.md` 必须至少维护：

- `lastUpdated`；
- `baselineCommit`；
- `latestMilestone`；
- 能力状态；
- 已完成范围；
- 明确未完成范围；
- 证据入口；
- 下一实施方向。

以下情况视为文档门禁失败：

- 新里程碑标记为 Implemented，但未更新 `implementation-status.md`；
- 状态总览声称完成，但没有代码、迁移、机器契约或测试证据；
- 删除、隐藏或模糊化未实现范围；
- 状态总览与实现文档、验收矩阵或仓库基线明显不一致且没有说明。

重大长期决策必须通过 ADR 保存，不得只写在注释或 PR 描述中。

---

## 12. Agent 变更流程

```text
1. 定位能力、模块和里程碑
2. 阅读事实源、实施状态与 ADR
3. 检查契约、迁移、追踪矩阵和验收矩阵
4. 说明实现方案与明确边界
5. 修改代码、契约、迁移和文档
6. 补充自动化测试
7. 运行相关验证
8. 更新 implementation-status.md
9. 检查所有事实源是否一致
10. 汇报已完成、未完成、验证结果和风险
```

最终说明至少包含：对应文档、修改模块和契约、事务/授权/幂等保证、测试结果、未实现内容、工作区/提交状态，以及是否需要 ADR 或后续迁移。

---

## 13. 例外与架构变更

偏离规范时不得静默实施，必须：

1. 指出冲突规则；
2. 解释不再适用的原因；
3. 评估领域、数据、契约、安全、部署和回滚影响；
4. 提供迁移与回退方案；
5. 新增或更新 ADR；
6. 同步事实源、实施状态和验收矩阵；
7. 获得用户或架构负责人明确批准。

---

## 14. 完成定义

一个任务只有同时满足以下条件才算完成：

- 实现符合相关产品和架构文档；
- 模块边界未被破坏；
- 数据库、API 和事件契约已同步；
- 事务、幂等、授权、审计和多租户规则得到保持；
- 必要测试和验收门禁通过；
- 里程碑实现文档和验收矩阵准确；
- `implementation-traceability-matrix.md` 已同步；
- `implementation-status.md` 已同步更新最新里程碑、能力状态、已完成范围、未实现范围、证据和下一方向；
- 根 README 与 Architecture Book 导航已同步；
- 没有隐藏失败、临时绕过或未说明的架构偏离。

未更新 `implementation-status.md` 的里程碑不得宣称完成或标记为 Implemented。

任何安全越权、租户泄露、事务不一致、消息丢失风险、状态机绕过、契约破坏、测试门禁规避或虚假进度声明，均属于阻断问题。
