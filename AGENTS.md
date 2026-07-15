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
8. 根据变更范围确定分级验证计划；
9. 然后才能编码。

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

## 10. 新系统演进与零兜底兼容

ServiceOS 是新系统。默认不存在必须长期兼容的历史实现、历史脏数据或旧客户端。开发过程中必须优先修正模型、数据、契约和调用方，禁止通过不断叠加兜底兼容逻辑掩盖问题。

默认禁止：

- 新旧字段、新旧状态、新旧接口同时长期保留；
- 字段缺失、值非法或状态未知时静默补默认值或猜测旧语义；
- `try/catch` 吞掉异常后返回空对象、默认成功或伪造降级结果；
- 为不存在的历史调用方预留旧 DTO、旧字段、旧事件版本或双写逻辑；
- 在核心代码中永久保留 `legacy`、`compat`、`fallback`、`temporary` 分支；
- 同时维护两套权威模型、状态机、字段语义或持久化路径；
- 用宽松解析、忽略未知字段或自动修正非法输入掩盖契约错误；
- 在生产代码中加入测试专用兜底；
- 将“先兼容，以后再删除”作为无期限方案。

正确做法：

1. 直接修正文档、代码、迁移、测试和调用方；
2. 对错误输入、缺失权威数据、未知状态和版本不一致采用失败关闭；
3. 切换实现时采用一次性迁移、原子切换、短期 feature gate 或受控发布；
4. 删除已被替代的字段、分支、适配器、配置和测试；
5. 评审必须阻断无业务依据的兜底、兼容和静默降级。

确有外部系统、已发布客户端或生产数据需要兼容时，必须同时满足：

- 有明确、真实、可验证的兼容对象；
- 新增或更新 ADR；
- 兼容逻辑集中在边界适配层；
- 设置负责人、截止日期和删除里程碑；
- 提供迁移、监控、验收和回退方案；
- 在 `implementation-status.md` 中登记临时技术债与退出条件；
- 到期必须删除。

无 ADR、无截止日期、无删除计划的兼容或兜底逻辑属于阻断问题。

---

## 11. 代码可读性、注释与中文表达

代码必须清晰、可维护、可审查。注释用于解释业务意图、约束和非显然原因，不能掩盖混乱结构。

- 复杂业务、较长方法、并发控制、事务编排、状态迁移、复杂 SQL、重试恢复、跨模块协作和安全校验必须有详细中文注释；
- 关键分支、锁与版本检查、幂等判断、失败关闭原因和副作用，应在代码附近添加行级或代码块级中文注释；
- 注释必须解释为什么这样做、保护什么不变量、失败有什么后果；
- 较长代码优先拆分职责，再给剩余复杂部分补充注释；
- 公共 API、SPI、领域命令、领域事件、复杂 Repository 方法和重要配置应有中文 Javadoc 或等价说明；
- TODO/FIXME 必须写明原因、责任范围和删除条件；
- 修改逻辑时必须同步更新失效注释；
- getter、简单赋值、普通循环和自解释代码不要求机械逐行注释。

日志、异常、校验、审计、告警和操作提示在不受外部协议约束时优先使用准确、完整、可检索的中文，并包含必要业务上下文。不得泄露密码、Token、密钥或敏感原文。对外协议字段、标准错误码、Schema、事件类型、指标名、Trace 属性和数据库对象名保持稳定英文标识。

关键复杂逻辑缺少必要注释，或日志异常信息无法定位，属于评审阻断项。

---

## 12. 测试与分级验证

质量保证采用“影响范围驱动的分级验证”。不得在每次小修改后机械执行全量 `./mvnw clean verify`，也不得以节省时间为理由跳过与改动风险直接相关的测试。

### 12.1 验证层级

#### L1：编码反馈

只编译受影响模块，默认不执行 `clean`：

```bash
./mvnw --no-transfer-progress -pl serviceos-backend -am -DskipTests compile
```

纯文档修改不要求运行 Maven 全量构建，但必须检查 Markdown、链接、索引和事实源一致性。

#### L2：变更点验证

每完成一个领域规则、Application Service、Mapper、Controller 或契约变更，优先运行直接相关测试：

```bash
./mvnw --no-transfer-progress -pl serviceos-backend -Dtest=RelevantTest test
```

数据库、并发、事务、唯一约束或 PostgreSQL 特性必须运行对应真实 PostgreSQL Testcontainers 集成测试。

#### L3：切片或模块回归

业务切片完成后，运行受影响模块及相邻模块的适用测试：领域单元测试、PostgreSQL IT、MVC 安全测试、事件 Schema 与幂等消费测试、OpenAPI 与客户端生成测试、Spring Modulith 模块边界测试，以及 Worker 并发/恢复测试。

#### L4：里程碑门禁

里程碑准备提交或宣称 `Implemented` 前，必须至少执行一次：

```bash
./mvnw --no-transfer-progress verify
```

并执行涉及的契约兼容、客户端生成、迁移或 staging 演练命令。全量验证失败时不得提交完成状态。

#### L5：干净构建与发布门禁

仅以下场景要求 `./mvnw clean verify`：

- PR 或主分支 CI 的干净工作区；
- 发布候选版本；
- 验证从零构建可复现；
- 父 POM、依赖、构建插件或生成代码机制变化；
- 怀疑缓存或生成产物污染；
- 用户或发布规范明确要求。

本地开发默认禁止无理由反复执行 `clean`。

### 12.2 Spring Modulith 模块边界独立强制门禁

Spring Modulith `ArchitectureTest` 是 ServiceOS 的独立架构门禁，不是普通可选回归测试，也不能被单元测试、接口测试、编译成功或全量业务用例替代。

出现下列任一变更时，必须显式执行 Spring Modulith 模块边界验证：

- 新增、删除、拆分、合并或重命名业务模块；
- 调整模块职责、依赖方向或跨模块调用；
- 修改 `package-info.java`、模块声明、命名接口或模块扫描配置；
- 新增或修改模块公开 API、SPI、领域事件发布/订阅关系；
- 移动类、Repository、Controller、配置或事件消费者到其他模块；
- 修改 `shared`、`bootstrap` 或可能被多个模块引用的公共能力；
- 修复循环依赖、内部包泄漏或非法跨模块访问；
- 里程碑引入新的模块协作关系。

最低验证命令应使用仓库中真实的架构测试类名；若当前类名为 `ArchitectureTest`，可执行：

```bash
./mvnw --no-transfer-progress -pl serviceos-backend \
  -Dtest=ArchitectureTest \
  test
```

如果仓库实际使用其他测试类名或多个 Modulith 架构测试，Agent 必须先检索并执行全部适用测试，不得机械照抄示例命令。

必须遵守：

- Modulith 门禁失败时不得通过扩大 `allowedDependencies`、开放内部包或移动代码到 `shared` 来绕过；
- 不得以“业务测试全部通过”为理由接受模块边界失败；
- 新增跨模块依赖前必须证明依赖方向符合架构文档，必要时更新 ADR；
- ArchitectureTest 必须纳入对应里程碑的 L3 回归和 L4 全量门禁证据；
- 最终报告必须单独列出 Modulith 验证命令和结果；
- 未执行适用的 Modulith 验证，或验证失败，均不得宣称里程碑完成。

### 12.3 按改动类型选择测试

Agent 必须依据 `git diff --name-only <baseline>...HEAD` 或等价方式分析影响范围：

| 改动类型 | 最低充分验证 |
|---|---|
| 纯文档 | Markdown、链接、索引、实施状态一致性 |
| 单个领域规则 | 相关单元测试 |
| Application Service | 单元测试 + 对应用例集成测试 |
| MyBatis Mapper / SQL | 对应 PostgreSQL IT |
| Flyway | 迁移验证 + 对应模块 PostgreSQL IT |
| Controller / HTTP API | MVC 测试 + OpenAPI 校验 |
| Event Schema | Schema 校验 + 兼容性检查 + 消费幂等测试 |
| 授权与 Scope | MVC 安全测试 + 拒绝审计测试 |
| Inbox / Outbox / Worker | 事务、幂等、claim/lease/retry/recovery 测试 |
| 模块声明、公共 API、跨模块调用或事件关系 | Spring Modulith ArchitectureTest（独立强制门禁） |
| shared、bootstrap、父 POM、公共契约 | ArchitectureTest + 受影响模块回归 + 全量 verify |
| 里程碑完成 | L3 模块回归 + ArchitectureTest（适用时必须显式执行）+ L4 全量 verify |

### 12.4 执行效率与输出控制

- 默认使用 `--no-transfer-progress`；
- 可使用 Maven fail-fast；
- 不得反复粘贴完整成功日志；
- 保留命令、结果摘要、失败根因和关键堆栈；
- 修复失败后先重跑直接失败测试，再升级到模块或全量验证；
- 不得为了减少日志或 token 而隐藏失败、删除断言或跳过测试。

### 12.5 最终报告要求

最终报告必须明确列出：

1. 影响范围判断依据；
2. 选择的 L1/L2/L3/L4/L5 验证；
3. 已运行命令和结果；
4. Spring Modulith ArchitectureTest 是否适用、执行命令及结果；
5. 未运行的全量或干净构建及原因；
6. 是否仍需 CI 执行 `clean verify`；
7. PostgreSQL、授权、契约、并发和模块边界测试是否已覆盖。

### 12.6 通用测试禁令

Agent 必须读取对应验收矩阵，将适用条目实现为自动化证据，或明确说明未满足原因。

禁止删除或跳过失败测试、放宽核心断言、将 CI 必跑测试改为默认跳过、用 Mock 替代必须验证的真实数据库或安全行为，或捕获异常后忽略失败。

复杂测试夹具、并发场景、故障注入和多阶段断言必须使用中文注释说明前置条件、动作和预期不变量。

---

## 13. 文档与实施状态同步

产品范围、领域规则、模块职责、聚合、状态机、API、事件、数据模型、安全、部署、验收或里程碑状态发生变化时，必须同步更新 `serviceos-architecture/`。

每个新里程碑或已实现范围变化，必须在同一提交或同一 PR 中同步更新：

1. `serviceos-architecture/README.md`；
2. 对应 `architecture/Mxx` 实现文档；
3. 对应 `testing/Mxx` 验收矩阵；
4. `serviceos-architecture/docs/implementation-traceability-matrix.md`；
5. `serviceos-architecture/docs/implementation-status.md`；
6. 根 README 当前可运行基线；
7. 相关总体设计文档的已实现和未实现边界；
8. 适用 OpenAPI、事件 Schema、Flyway 和部署迁移清单。

`implementation-status.md` 必须维护 `lastUpdated`、`baselineCommit`、`latestMilestone`、能力状态、已完成范围、未完成范围、证据入口和下一方向。

新里程碑标记为 Implemented 但未更新状态总览，状态总览无工程证据，隐藏未实现范围，或与仓库基线明显不一致，均属于文档门禁失败。

重大长期决策必须通过 ADR 保存。

---

## 14. Agent 变更流程

```text
1. 定位能力、模块和里程碑
2. 阅读事实源、实施状态与 ADR
3. 检查契约、迁移、追踪矩阵和验收矩阵
4. 说明实现方案、明确边界和分级验证计划
5. 修改代码、契约、迁移和文档
6. 清理被替代代码，确认没有无依据兼容或兜底
7. 为复杂逻辑补中文注释，为日志和异常补可定位中文信息
8. 补充自动化测试
9. 执行变更点验证和模块回归
10. 适用时显式执行 Spring Modulith ArchitectureTest
11. 里程碑完成前执行全量 verify
12. 更新 implementation-status.md
13. 检查所有事实源一致性
14. 汇报完成范围、未完成范围、验证结果和风险
```

最终说明至少包含：对应文档、修改模块和契约、事务/授权/幂等保证、测试结果、Modulith 门禁结果、未实现内容、是否引入兼容逻辑、是否清理替代代码、工作区/提交状态，以及是否需要 ADR 或后续迁移。

---

## 15. 例外与架构变更

偏离规范时不得静默实施，必须：

1. 指出冲突规则；
2. 解释不再适用原因；
3. 评估领域、数据、契约、安全、部署和回滚影响；
4. 提供迁移与回退方案；
5. 新增或更新 ADR；
6. 同步事实源、实施状态和验收矩阵；
7. 获得用户或架构负责人明确批准。

---

## 16. 完成定义

一个任务只有同时满足以下条件才算完成：

- 实现符合产品和架构文档；
- 模块边界未被破坏；
- 涉及模块结构、公共 API、跨模块调用或事件关系时，Spring Modulith ArchitectureTest 已显式执行并通过；
- 数据库、API 和事件契约已同步；
- 事务、幂等、授权、审计和多租户规则得到保持；
- 与改动风险直接相关的精准测试、模块回归和里程碑全量门禁已通过；
- 复杂逻辑、关键分支、并发与事务代码有足够中文注释；
- 日志、异常、校验和操作提示优先使用准确、可定位的中文；
- 没有无业务依据的兼容分支、默认值兜底、静默降级、双轨模型或遗留代码；
- 确需兼容的逻辑有 ADR、负责人、截止日期、删除里程碑和自动化证据；
- 里程碑实现文档、验收矩阵、追踪矩阵和状态总览准确；
- 根 README 与 Architecture Book 导航已同步；
- 没有隐藏失败、临时绕过或未说明的架构偏离。

未更新 `implementation-status.md` 的里程碑不得宣称完成或标记为 Implemented。

任何安全越权、租户泄露、事务不一致、消息丢失风险、状态机绕过、契约破坏、Modulith 模块边界失败、测试门禁规避、虚假进度声明、无期限兼容兜底或关键复杂逻辑不可审查，均属于阻断问题。
