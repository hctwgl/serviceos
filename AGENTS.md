# ServiceOS Agent Development Guide

本文件是 ServiceOS 仓库级开发入口。目标是在不牺牲领域边界、事务可靠性、安全和可验证性的前提下，支持小步、并行、快速迭代。

`serviceos-architecture/` 是产品与架构事实源；本文件只规定如何高效地定位事实、实施变更、选择验证范围和维护里程碑状态。

---

## 1. 项目与工作目录

ServiceOS 是面向新能源充电设施现场服务的可配置履约平台，采用 Java 21、Spring Boot、Spring Modulith、PostgreSQL、Flyway、MyBatis、OpenAPI、事件 Schema、Inbox/Outbox、OIDC/JWT、Capability 和 Tenant/Project Scope。

必须在真实仓库 `/Users/louis/code/serviceos` 工作。开始任务先执行 `git status --short --branch`，保留用户已有修改，不向镜像、导出副本或历史任务目录写代码。

默认工程目标：模块边界清晰、事务可靠、失败关闭、可重放、可审计、可测试。不得为了短期 CRUD 速度或形式上的框架统一破坏这些目标。

---

## 2. 事实源与优先级

按任务需要渐进读取，不要求每个小改动遍历整本 Architecture Book。

### 2.1 固定入口

- `serviceos-architecture/docs/agent-navigation.md`：任务类型 → 最小必读路由表，探索从这里开始；
- `serviceos-architecture/docs/implementation-status.md`：当前基线、能力总览、完成和未完成边界（里程碑历史摘要在 `implementation-status-archive.md`）；
- `serviceos-architecture/docs/milestone-index.md`：一行一里程碑的实现文档/验收矩阵索引（脚本生成；通过 `bash scripts/find-milestone.sh <查询>` 定位，不整份读取）；
- `serviceos-architecture/docs/implementation-traceability-matrix.md`：能力到代码、契约和测试的追踪；
- `serviceos-architecture/README.md`：Architecture Book 导航；
- `serviceos-architecture/architecture/`：总体设计与 Mxx 实现文档；
- `serviceos-architecture/testing/`：验收矩阵；
- `serviceos-architecture/decisions/`：ADR；
- `serviceos-contracts/`：OpenAPI 与事件 Schema；
- `serviceos-backend/AGENTS.md`：后端模块地图与测试布局；
- `serviceos-backend/src/main/resources/db/migration/`：数据库迁移事实。

### 2.2 冲突优先级

1. 当前任务中用户明确批准的最新决策；
2. 已接受 ADR；
3. Accepted/Implemented 的架构与验收文档；
4. OpenAPI、事件 Schema、Flyway 等机器契约；
5. 自动化测试证明的行为；
6. 当前代码；
7. README、注释和历史说明。

代码与文档冲突时不得默认代码正确。能从高优先级事实判断时直接修复；涉及核心业务语义、破坏性契约或长期架构选择时再请求确认。

### 2.3 状态含义

- `Draft`：形成中，不可作为研发承诺；
- `Proposed`：完整提案，尚未接受；
- `Accepted`：可指导实现，不代表代码完成；
- `Implemented`：声明范围已有代码、契约、迁移和适用自动化证据。

`Implemented` 只证明里程碑声明范围，不能外推为整个领域完成。

---

## 3. 快速迭代分级

先按风险选择流程，避免所有任务机械套用最大门禁。

| 等级 | 典型变更 | 开发前最低阅读 | 交付门禁 |
|---|---|---|---|
| R0 | 文案、注释、索引、无语义重构 | 当前文件及直接引用 | diff、链接/格式检查 |
| R1 | 局部缺陷、单一领域规则、内部重构 | 实施状态、对应实现文档、直接测试 | 精准测试；涉及 SQL 则 PostgreSQL IT |
| R2 | 新用例、跨模块协作、API/事件/Flyway | R1 + ADR、契约、数据模型、验收矩阵 | 模块回归、契约/迁移门禁、ArchitectureTest |
| R3 | 新里程碑、架构/状态机/安全边界、发布候选 | 全部相关事实源与追踪矩阵 | 全量 verify、适用 staging/兼容/回滚门禁、完整文档同步 |

风险按最高影响项升级。一个小 diff 只要改变授权、租户范围、状态机、事务边界、事件语义或数据库演进，就至少是 R2。

### 3.1 开发前最小动作

1. 明确能力、模块、里程碑或缺陷范围；
2. 按 `agent-navigation.md` 路由表确定最小阅读集，用 `scripts/find-milestone.sh` 定位具体文档；
3. 查看实施状态和直接相关事实源；
4. 检查公共 API、依赖方向及适用契约/迁移；
5. 写下本次明确不做的边界；
6. 选择 R0～R3 和验证命令；
7. 开始编码。

禁止先写实现，再寻找文档背书；也禁止因非核心细节不完整而无限停留在分析阶段。对可逆、局部、不改变业务语义的细节可作合理假设并在交付说明中记录。禁止批量通读 `architecture/`、`testing/` 目录找上下文；标准里程碑循环见 `serviceos-architecture/docs/milestone-playbook.md`。

### 3.2 必须确认的决策

以下事项没有高优先级事实时必须暂停并请求用户或架构负责人确认：

- 核心业务状态、责任归属、计价/SLA/审核处置规则；
- 破坏性 API、事件或数据迁移；
- 授权、Tenant/Project Scope、敏感数据策略；
- 新模块、职责迁移、长期外部兼容或基础设施选型；
- 会产生不可逆外部副作用或生产数据损失的操作。

其余实现细节优先按现有模式推进，不把普通命名、局部结构或测试组织升级为阻塞问题。

---

## 4. 模块与分层硬约束

ServiceOS 是模块化单体，不是无边界单体：

```text
Interface / Adapter
    -> Application
    -> Domain
    -> Port / Repository Interface
    -> Infrastructure Adapter
```

- 模块只通过公开 API、领域事件或明确 SPI 协作；
- 禁止跨模块访问内部包、内部 Repository 或内部表；
- Controller 只做协议适配；Application Service 编排用例和事务；Domain 维护规则与不变量；Infrastructure 隔离技术实现；
- Domain 不得依赖 Web、MyBatis、JDBC、消息客户端或外部 SDK；
- 禁止通用 CRUD 绕过状态机、授权、审计、幂等或版本检查；
- `shared` 只放稳定、无业务归属的最小公共能力；
- 新模块、职责调整或依赖方向变化必须同步架构文档，重大变化更新 ADR。

Spring Modulith 模块边界失败是阻断问题，不得通过扩大 `allowedDependencies`、开放内部包或把业务代码移入 `shared` 绕过。

---

## 5. 事务、并发与可靠消息

- 聚合修改、审计、幂等结果和 Outbox 必须同事务提交；
- 外部请求、事件和回调必须定义幂等键及重复语义；
- 事件消费者使用 Inbox 或等价唯一约束去重；
- Worker 使用可恢复的 claim/lease/retry，包含上限、退避和人工接管；
- 并发迁移包含状态、版本或等价条件，并检查影响行数；
- 外部网络调用不得位于持有数据库锁的长事务；
- 不得提交后临时发消息、吞异常、伪造成功或把未知结果当成功。

复杂事务必须在代码附近用中文说明事务边界、幂等键、锁顺序、失败结果和恢复方式。

---

## 6. 持久化与数据库

详细规范见 `serviceos-architecture/architecture/36-persistence-engineering-guideline.md`。

- 业务持久化默认 MyBatis，复杂查询默认 MyBatis XML；
- Domain/Application 通过 Repository 端口访问数据，不直接依赖 Mapper、JdbcTemplate 或 JdbcClient；
- 核心业务禁止用 MyBatis-Plus 通用 CRUD 代替领域命令；
- Spring JDBC 仅用于需要精确控制 SQL、锁或并发语义的内核，并说明理由；
- 结构变化只通过连续 Flyway 迁移，默认 expand/contract；
- runtime 账号不得拥有 DDL 权限；
- 多租户数据显式包含 tenant/project scope；
- 锁、claim、lease、唯一约束、迁移和 PostgreSQL 特性必须由真实 PostgreSQL Testcontainers 证明，不能只用 H2 或 Mock。

迁移不得留下无期限默认值、双写或旧查询回退。一次性回填完成后应删除临时兼容结构。

---

## 7. API、事件、安全与文件

- OpenAPI 是 HTTP 契约事实源；JSON Schema 是事件契约事实源；
- 修改 Controller/DTO/事件时同步机器契约和契约测试；
- 已发布契约不得原地破坏字段类型或语义，破坏性变更必须获批并版本化；
- 身份来自 OIDC/JWT，授权基于 Capability 与 Tenant/Project Scope；
- 不信任客户端 tenant、project、operator 或权限结果；前端隐藏按钮不能代替后端授权；
- 车企接入必须验签、校验时间窗、防重放并幂等；
- Secret 不得进入仓库、镜像、日志、Trace 或测试快照；
- 文件生命周期保持 `Begin -> 受限上传 -> Finalize -> 隔离 -> 扫描 -> 授权下载`；
- 禁止未扫描下载、公共暴露私有文件、长期保存大文件正文或永久暴露对象存储 URL；
- 新链路保持 W3C Trace Context、correlation ID、结构化日志、脱敏、业务审计和正确健康探针。

授权绕过、租户越权、审计缺失和敏感信息泄露均为阻断问题。

---

## 8. 新系统演进：唯一权威路径

ServiceOS 是新系统。默认直接修正模型、契约、迁移、测试和调用方，不为假设中的旧客户端或脏数据增加永久兼容。

禁止：

- 长期保留新旧字段、状态、接口、模型或持久化双轨；
- 缺值、非法值或未知状态时静默补默认值、猜测语义或返回默认成功；
- 吞异常后返回空对象、空集合或伪造降级；
- 无真实对象的 `legacy`、`compat`、`fallback`、`temporary` 分支；
- 宽松解析、忽略未知字段、生产测试兜底；
- “先兼容以后再删”但没有期限和退出证据。

真实外部系统、已发布客户端或生产数据确需兼容时，必须有 ADR、明确对象、边界适配层、负责人、截止日期、删除里程碑、迁移/监控/回退方案和自动化证据，并登记实施状态。

---

## 9. 代码与中文表达

- 优先用清晰结构表达意图，不用注释掩盖超长方法；
- 复杂业务规则、事务、并发、状态迁移、SQL、重试、安全和跨模块协作必须有足够中文注释；
- 注释解释“为什么、保护什么不变量、失败后果”，不逐字翻译代码；
- 公共 API/SPI、领域命令/事件、复杂 Repository 和重要配置使用中文 Javadoc 或等价说明；
- TODO/FIXME 写明原因、责任范围和删除条件；修改逻辑同步更新注释；
- getter、简单赋值和自解释代码不要求机械注释；
- 不受外部协议约束的日志、异常、校验、审计和操作提示优先使用准确、可检索的中文，并包含必要业务上下文；
- 错误码、Schema、事件类型、指标名、Trace 属性、数据库对象名和第三方协议保持稳定英文标识。

---

## 10. 精准验证阶梯

目标是尽快发现与改动最相关的问题，再按风险升级；不得每次小改动机械运行全量干净构建，也不得用“快速迭代”跳过适用门禁。

### L0：静态检查

- `git diff --check`；
- 文档链接、索引和事实源一致性；
- 必要的格式、Schema 或静态分析。

### L1：快速反馈

```bash
./mvnw --no-transfer-progress -pl serviceos-backend -am -DskipTests compile
./mvnw --no-transfer-progress -pl serviceos-backend -Dtest=RelevantTest test
```

修复失败后先重跑直接失败测试，不重复执行无关全量测试。
纯文档修改不要求运行 Maven 全量构建，但必须检查 Markdown、链接、索引和事实源一致性。

### L2：风险专项

| 改动 | 最低专项证据 |
|---|---|
| 领域规则/Application Service | 相关单元测试 + 用例集成测试 |
| MyBatis/SQL/Flyway | 对应 PostgreSQL IT；Flyway 还需迁移验证 |
| Controller/OpenAPI | MVC/安全测试 + OpenAPI 校验 |
| Event Schema/消费者 | Schema/兼容检查 + Inbox 幂等测试 |
| 授权/Scope | 允许与拒绝路径 + 拒绝审计 |
| Inbox/Outbox/Worker | 事务、重复、claim/lease/retry/recovery |
| 模块声明、公开 API、跨模块调用/事件 | Spring Modulith ArchitectureTest |
| shared/bootstrap/父 POM | ArchitectureTest + 受影响回归 |

ArchitectureTest 适用时显式运行仓库中的真实测试类，例如：

```bash
./mvnw --no-transfer-progress -pl serviceos-backend -Dtest=ArchitectureTest test
```

### L3：里程碑门禁

R3 或准备标记 `Implemented` 前至少运行一次。

在 Apple Silicon（M 系列）且使用 OrbStack/Docker 的本地环境，必须通过仓库脚本执行，使 Testcontainers 使用宿主机原生 PostgreSQL 镜像：

```bash
bash scripts/verify-local.sh
```

非交互调用默认只回传构建摘要并将完整 Maven 日志写入 `target/verification-logs/`；失败仍回传
关键错误和日志尾部。需要实时查看全部输出时显式设置 `SERVICEOS_VERIFY_OUTPUT=full`，该选项只改变
日志呈现，不得改变或跳过任何验证参数。

其他本地环境在脚本可用时也应优先使用同一入口；CI 环境可继续直接执行：

```bash
./mvnw --no-transfer-progress verify
```

并执行适用的契约兼容、客户端生成、迁移、staging、回滚或 smoke 演练。失败时不得提交完成状态。

### L4：干净构建/发布

仅发布候选、主分支 CI、手工最终候选验证、构建机制变化、从零可复现验证、怀疑缓存污染或用户明确要求时执行。

Apple Silicon + OrbStack/Docker 本地环境必须执行：

```bash
bash scripts/verify-local.sh clean verify
```

CI 或架构已由流水线明确控制的环境可执行：

```bash
./mvnw --no-transfer-progress clean verify
```

### 10.1 Apple Silicon / OrbStack 强制约束

- 不得在本地验证中设置 `DOCKER_DEFAULT_PLATFORM=linux/amd64`；
- 不得在 Testcontainers 创建命令中强制 `linux/amd64`；
- 不得绕过 `scripts/verify-local.sh` 后继续接受 OrbStack 的跨架构模拟结果；
- 脚本只修正当前验证子进程的架构环境，不得擅自修改用户永久 shell 配置；
- 本地已有正确架构的 PostgreSQL 测试镜像时必须直接复用，不得每次验证都执行 `docker pull`、删除镜像或强制刷新；
- 只有本地首次缺少镜像，或明确设置 `SERVICEOS_TEST_REFRESH_IMAGE=true` 时才允许访问远端镜像仓库；
- 脚本用于选择原生镜像和复用本地缓存，不得借此跳过 PostgreSQL、Flyway、事务、并发、授权或 Spring Modulith 门禁；
- 如确有跨架构测试要求，必须有明确技术依据、ADR、独立验证任务和退出计划，不能作为日常默认路径。

精准 PostgreSQL 测试也应通过脚本透传 Maven 参数，例如：

```bash
bash scripts/verify-local.sh \
  -pl serviceos-backend \
  -Dtest=RelevantPostgresIT \
  test
```

测试禁令：不得删除或跳过失败测试、放宽核心断言、默认跳过 CI 门禁、用 Mock 替代必须证明的 PostgreSQL/安全行为，或捕获异常后忽略失败。

### 10.2 GitHub Actions 反馈节奏

- Agent 不得在每次中间提交或 push 后停下等待 GitHub Actions；开发过程中应继续使用 L0～L2
  精准本地测试推进同一切片；
- PR 流水线按文件变化运行最低充分门禁：纯文档只做 Preflight，Admin Web、Backend、Contracts
  和 Deployment 分别触发对应验证；未知或 CI 基础设施变化保守升级为完整 PR 验证；
- 同一 PR 的新提交由 workflow `concurrency` 自动取消旧运行。旧运行被取消是预期反馈收敛，
  不得当作产品或测试失败重复修复；
- 只有最终候选 HEAD 已冻结、精准本地测试通过且不再计划追加提交时，Agent 才通过
  `workflow_dispatch` 等待一次完整远端验证；
- `container-staging` 只属于 `master` push、明确手工验证或发布候选，不属于普通 PR；
- 最终候选完整验证和 `master` 门禁仍必须保留 PostgreSQL、Flyway、契约兼容、安全扫描、
  Spring Modulith、Admin E2E、镜像构建、迁移、Smoke、回滚与恢复演练，不得因分层触发而删除。

---

## 11. 文档同步按影响范围执行

不要为局部实现机械改写所有文档；只同步被事实变化影响的权威文件。

| 变化 | 必须同步 |
|---|---|
| 纯实现修复且外部事实不变 | 测试；必要时修正失效注释/实现文档 |
| API/事件/数据库变化 | 机器契约或 Flyway + 对应架构/数据/验收文档 |
| 领域规则、状态机、模块职责变化 | 总体设计 + ADR（重大时）+ 验收矩阵 |
| 新里程碑或 Implemented 范围变化 | 下列完整里程碑清单 |

完整里程碑清单：

1. `serviceos-architecture/README.md`；
2. 对应 `architecture/Mxx` 实现文档；
3. 对应 `testing/Mxx` 验收矩阵；
4. `implementation-traceability-matrix.md`；
5. `implementation-status.md`；
6. 根 README 当前可运行基线；
7. 被影响总体设计中的已实现/未实现边界；
8. 适用 OpenAPI、事件 Schema、Flyway 和部署迁移清单；
9. 运行 `bash scripts/generate-milestone-index.sh`，从权威实现文档重新生成 `milestone-index.md`；`implementation-status-archive.md` 为冻结历史，不再追加重复摘要。

`implementation-status.md` 维护 `lastUpdated`、`baselineCommit`、`latestMilestone`、能力状态、完成/未完成范围、证据和下一方向。提交 SHA 需先生成时，允许功能提交后立即用独立文档提交回填，不因此阻塞功能提交。

没有工程证据不得标记 Implemented；不得删除或模糊未实现范围。

---

## 12. 推荐迭代节奏

标准里程碑循环的逐步命令块见 `serviceos-architecture/docs/milestone-playbook.md`。

```text
1. git status，确认基线与已有修改
2. 定位能力/模块，选择 R0～R3
3. 渐进读取直接事实源，列出范围与非目标
4. 小步修改代码、契约、迁移、测试和必要文档
5. 每个逻辑小步运行 L0/L1；风险点运行 L2；中间 push 后继续开发，不等待远端流水线
6. 清理被替代代码和无依据兜底
7. 切片完成后做相邻模块回归
8. 冻结最终候选 HEAD 后执行 L3，并只等待一次完整远端验证；L4/staging 仅用于发布候选、手工或 master
9. 同步实施状态和追踪矩阵
10. 检查 diff、工作区和事实源一致性后提交
```

优先保持提交单一意图：实现与对应测试/文档可同提交；必须回填未知提交 SHA 时使用紧随其后的状态提交。不得把无关格式化、依赖升级或用户已有修改混入当前提交。

---

## 13. 完成与汇报

本节是完成门禁摘要，不替代第 4～11 节。任何任务必须同时满足这些章节中与本次改动适用的
模块、事务、数据库、契约、安全、文件、兼容、代码质量、验证和文档硬约束；不能因为本节未
重复某条规则就将其视为可选。

### 局部任务完成

- 请求范围已实现，非目标、未实现内容和已知风险已明确；
- 实现符合适用产品、架构、ADR 和验收文档，没有用当前代码反向覆盖高优先级事实；
- 模块依赖方向、领域不变量和状态机未被破坏，没有跨模块访问内部包、Repository 或表；
- 事务、幂等、并发、审计、Tenant/Project Scope 和失败关闭语义得到保持；
- 受影响的数据库、HTTP API、事件、文件及外部集成契约已同步；
- 与风险直接相关的最低充分验证已通过，适用的 PostgreSQL、授权、契约、Worker 和
  ArchitectureTest 证据完整；
- 复杂业务、事务、并发、SQL、安全和恢复逻辑具有足够中文注释，日志、异常和校验信息可定位
  且不泄露敏感数据；
- 被替代的字段、分支、适配器、配置和测试已经清理，没有无依据默认值、静默降级、测试兜底、
  双轨模型或无期限兼容；
- 确需兼容时已有真实兼容对象、ADR、负责人、截止日期、删除里程碑、迁移/监控/回退方案和
  自动化证据；
- 受影响事实源、注释、测试和文档已同步，工作区没有混入无关修改；
- 没有隐藏失败、跳过门禁、放宽核心断言或未说明的架构偏离。

### 里程碑完成

除上述条件外，还必须：

- 里程碑声明范围的代码、数据库迁移、机器契约和适用自动化证据全部存在；
- 实现文档、验收矩阵、追踪矩阵、状态总览、Architecture Book 导航和根 README 相互一致；
- OpenAPI、事件 Schema、Flyway、客户端生成物和部署迁移清单按影响范围同步；
- 领域单元测试、真实 PostgreSQL IT、授权/拒绝审计、契约、幂等消费、Worker 恢复和模块边界
  等适用证据已完成；
- ArchitectureTest（适用时）已显式执行，L3 全量 `verify` 已通过；发布候选还必须满足适用的
  L4、staging、smoke 和回滚演练；
- 已实现范围、明确未实现范围、证据入口、风险和下一实施方向均准确记录，没有把局部切片外推
  为整个领域或完整平台完成；
- `implementation-status.md` 的 `lastUpdated`、`latestMilestone`、能力状态和 `baselineCommit`
  已回填，且基线提交可以从仓库历史验证；
- 需要 ADR、数据迁移、兼容退出或后续清理的事项已登记责任人与退出条件。

最终报告保持紧凑，至少说明：完成内容、影响模块/契约/迁移、事务/授权/幂等保证、实际验证命令与结果、未实现或未运行项及原因、兼容/清理情况、工作区和提交状态。只有 ArchitectureTest 适用时才单列其结果。

安全越权、租户泄露、事务不一致、消息丢失、状态机绕过、契约破坏、模块边界失败、测试门禁规避、虚假进度、无期限兼容或不可审查的关键复杂逻辑，均为阻断问题。

---

## 14. 例外

确需偏离本指南时，必须说明冲突规则、原因、影响、迁移与回退方案；涉及长期架构、数据、安全或契约的偏离需更新 ADR、事实源和验收矩阵，并获得明确批准。局部、可逆且不改变业务语义的实现调整不需要为形式而新增 ADR。

## 身份与组织治理正式执行入口

项目负责人已确认将身份与组织治理序列重编号为 **M183～M188**，承接已实现的 Admin Pilot
M135～M182，不得再使用计划草案中的旧号 M135～M140，也不得覆盖历史里程碑文档。

涉及用户、组织、网点人员、师傅、角色授权或 Portal 上下文时，先阅读下列内容事实源，并以
`implementation-status.md` 的 `latestMilestone` 为当前工程基线：

- `serviceos-architecture/roadmap/03-identity-organization-governance-delivery-plan.md`
- `serviceos-architecture/testing/identity-organization-governance-program-acceptance.md`
- `serviceos-architecture/roadmap/04-identity-organization-governance-agent-worklist.md`

不得创建保存密码的本地万能用户表，不得用单一 `user_type`、Keycloak Group、菜单或前端 scope 代替 Principal/Persona/Membership/RoleGrant 的权威边界。默认一个里程碑一个 Draft PR；只有对应工程证据成立后才更新 `latestMilestone` 或声明 IMPLEMENTED。
